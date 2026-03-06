/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2026, University of Oxford.
 *
 * University of Oxford means the Chancellor, Masters and Scholars of the
 * University of Oxford, having an administrative office at Wellington
 * Square, Oxford OX1 2JD, UK.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.project.surimi.poseidon.regulations;

import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.agents.market.Sale;
import uk.ac.ox.poseidon.agents.regulations.TemporalFishingAction;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.events.Listener;
import uk.ac.ox.poseidon.regulations.Regulations;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.time.ZoneOffset.UTC;

/**
 * Total Allowable Catch (TAC) regulation for a simplified fishery-wide closure model.
 * <p>
 * This regulation consumes {@link Sale} events and accumulates landed biomass per quota interval
 * and per quota-species. Fishing is permitted unless a requested fishing action overlaps a closed
 * portion of at least one configured TAC interval.
 * <p>
 * Model assumptions implemented here:
 * <ul>
 *     <li>TAC scope is limited to species plus time. Area, stock, fleet-segment, and actor-level
 *     dimensions are intentionally out of scope for now.</li>
 *     <li>{@link Sale} is the accounting trigger. In the current toy model, sale time is used as
 *     a proxy for immediate landing and quota visibility.</li>
 *     <li>Both sold and unsold biomass count against TAC. Unsold biomass is assumed to mean landed
 *     but not marketed, not discarded.</li>
 *     <li>Closure is fishery-wide within each quota interval: once any configured quota-species
 *     reaches or exceeds its limit at time {@code t}, fishing is closed from {@code t} until the
 *     end of that quota interval.</li>
 *     <li>Closure is sticky. Once an interval closes, later events do not reopen it.</li>
 *     <li>Overlapping quota intervals are allowed. A sale contributes to every configured interval
 *     whose time window contains the sale timestamp.</li>
 * </ul>
 * <p>
 * Quota definition rules:
 * <ul>
 *     <li>Quota units are kilograms.</li>
 *     <li>Quota definitions are immutable per interval/species pair. Re-setting the same pair is
 *     rejected.</li>
 *     <li>Overlapping quota-species definitions within the same interval are rejected. For
 *     example, defining both a generic species quota and a stage-specific quota for the same
 *     species code in one interval is not allowed.</li>
 *     <li>Species matching for catch accounting uses {@link Species#covers(Species)} semantics,
 *     not strict equality. A generic quota such as "HKE" therefore counts staged catches such as
 *     "HKE;juvenile", provided no overlapping quota definition for that species code exists in the
 *     same interval.</li>
 * </ul>
 * <p>
 * Internal representation notes:
 * <ul>
 *     <li>Configured quotas are stored by parent quota interval and quota-species.</li>
 *     <li>Accumulated catches are stored by parent quota interval and matched quota-species.</li>
 *     <li>Closures are stored by parent quota interval plus the first exhaustion timestamp.
 *     Effective closed sub-intervals are derived as needed as
 *     {@code [closureStart, quotaIntervalEnd)}.</li>
 *     <li>Intervals are treated independently; closure in one interval does not imply closure in
 *     another unless a fishing action overlaps both.</li>
 * </ul>
 */
public class TotalAllowableCatchQuotas implements Regulations<TemporalFishingAction>, Listener<Sale> {

    /**
     * Configured TAC quotas in kilograms, keyed by interval and quota-species.
     */
    private final Map<Interval, Map<Species, Double>> quotas = new HashMap<>();

    /**
     * Accumulated landed catches in kilograms, keyed by interval and quota-species.
     * <p>
     * Keys in this map align with quota-species (not necessarily the raw sold species), because
     * catches are recorded after applying {@link Species#covers(Species)} matching.
     */
    private final Map<Interval, Map<Species, Double>> catches = new HashMap<>();

    /**
     * Closure start instants keyed by their parent quota interval.
     */
    private final Map<Interval, Instant> closureInstants = new LinkedHashMap<>();

    /**
     * Returns whether a fishing action is permitted in time.
     * <p>
     * The action is rejected if its interval overlaps any effective closure interval.
     *
     * @param action fishing action to evaluate
     * @return {@code true} when the action does not overlap any closed interval
     */
    @Override
    public boolean isPermitted(final TemporalFishingAction action) {
        return getEffectiveClosureIntervals().stream().noneMatch(action.getInterval()::overlaps);
    }

    /**
     * Sets a quota for a given interval/species pair.
     * <p>
     * Quota definitions are immutable once set for a given interval/species pair. Overlapping
     * quota-species definitions within the same interval are also rejected.
     *
     * @param interval  interval to which the quota applies
     * @param species   quota-species key
     * @param quotaInKg allowed biomass in kilograms (must be non-negative)
     */
    public void setQuota(
        final Interval interval,
        final Species species,
        final double quotaInKg
    ) {
        checkArgument(quotaInKg >= 0.0, "Quota must be non-negative");
        final Interval quotaInterval = checkNotNull(interval);
        final Species quotaSpecies = checkNotNull(species);
        final Map<Species, Double> intervalQuotas =
            quotas.computeIfAbsent(quotaInterval, ignored -> new HashMap<>());
        validateNewQuotaDefinition(quotaInterval, intervalQuotas, quotaSpecies);
        intervalQuotas.put(quotaSpecies, quotaInKg);
    }

    @Override
    public Class<? extends Sale> getEventClass() {
        return Sale.class;
    }

    /**
     * Processes a sale event and updates TAC accounting.
     * <p>
     * Workflow:
     * <ul>
     *     <li>Select all quota intervals that contain the sale timestamp.</li>
     *     <li>For each sold item, add sold kilograms to every quota-species that covers the
     *     sold species.</li>
     *     <li>For each unsold catch species entry, add unsold kilograms to every quota-species
     *     that covers that species.</li>
     *     <li>Re-check closure condition for each affected interval.</li>
     * </ul>
     *
     * @param event sale event to account
     */
    @Override
    public void receive(final Sale event) {
        final Sale sale = checkNotNull(event);
        final var saleInstant = sale.getDateTime().toInstant(UTC);
        for (final Interval quotaInterval : relevantIntervalsFor(saleInstant)) {
            final Map<Species, Double> intervalQuotas = quotas.get(quotaInterval);
            if (intervalQuotas == null || intervalQuotas.isEmpty()) continue;
            final Map<Species, Double> intervalCatches =
                catches.computeIfAbsent(quotaInterval, ignored -> new HashMap<>());
            recordSoldCatch(sale, intervalQuotas, intervalCatches);
            recordUnsoldCatch(sale, intervalQuotas, intervalCatches);
            recordClosureIfAnyQuotaReached(quotaInterval, saleInstant);
        }
    }

    /**
     * Returns effective closure intervals derived from the recorded exhaustion timestamps.
     *
     * @return closed sub-intervals in insertion order
     */
    public List<Interval> getEffectiveClosureIntervals() {
        return closureInstants
            .entrySet()
            .stream()
            .map(entry -> Interval.of(entry.getValue(), entry.getKey().getEnd()))
            .toList();
    }

    private List<Interval> relevantIntervalsFor(final Instant instant) {
        return quotas
            .keySet()
            .stream()
            .filter(quotaInterval -> quotaInterval.contains(instant))
            .toList();
    }

    private static void validateNewQuotaDefinition(
        final Interval quotaInterval,
        final Map<Species, Double> intervalQuotas,
        final Species quotaSpecies
    ) {
        if (intervalQuotas.containsKey(quotaSpecies)) {
            throw new IllegalArgumentException(
                "Quota already defined for interval %s and species '%s'. Existing quota cannot be replaced."
                    .formatted(quotaInterval, quotaSpecies)
            );
        }
        final Species conflictingSpecies = intervalQuotas
            .keySet()
            .stream()
            .filter(existingSpecies -> quotaSpeciesOverlap(existingSpecies, quotaSpecies))
            .findFirst()
            .orElse(null);
        if (conflictingSpecies != null) {
            throw new IllegalArgumentException(
                "Cannot define quota for species '%s' in interval %s because overlapping quota species '%s' is already configured."
                    .formatted(
                        quotaSpecies,
                        quotaInterval,
                        conflictingSpecies
                    )
            );
        }
    }

    private static boolean quotaSpeciesOverlap(
        final Species firstSpecies,
        final Species secondSpecies
    ) {
        return firstSpecies.covers(secondSpecies) || secondSpecies.covers(firstSpecies);
    }

    private static void recordSoldCatch(
        final Sale sale,
        final Map<Species, Double> intervalQuotas,
        final Map<Species, Double> intervalCatches
    ) {
        for (final Sale.Item item : sale.getItems()) {
            recordCatch(intervalQuotas, intervalCatches, item.getSpecies(), item.getContent().asKg());
        }
    }

    private static void recordUnsoldCatch(
        final Sale sale,
        final Map<Species, Double> intervalQuotas,
        final Map<Species, Double> intervalCatches
    ) {
        for (final var bucket : sale.getUnsold().getBuckets().values()) {
            bucket.forEach((caughtSpecies, content) ->
                recordCatch(intervalQuotas, intervalCatches, caughtSpecies, content.asKg())
            );
        }
    }

    private static void recordCatch(
        final Map<Species, Double> intervalQuotas,
        final Map<Species, Double> intervalCatches,
        final Species caughtSpecies,
        final double caughtKg
    ) {
        final List<Species> matchedQuotaSpecies = matchingQuotaSpecies(intervalQuotas, caughtSpecies)
            .toList();
        matchedQuotaSpecies.forEach(
            quotaSpecies -> intervalCatches.merge(quotaSpecies, caughtKg, Double::sum)
        );
    }

    private static Stream<Species> matchingQuotaSpecies(
        final Map<Species, Double> intervalQuotas,
        final Species caughtSpecies
    ) {
        return intervalQuotas
            .keySet()
            .stream()
            .filter(quotaSpecies -> quotaSpecies.covers(caughtSpecies));
    }

    /**
     * Records closure start for an interval if at least one configured quota has been reached or
     * exceeded.
     *
     * @param quotaInterval quota interval to evaluate
     * @param closureStart start instant of the effective closed sub-interval
     */
    private void recordClosureIfAnyQuotaReached(
        final Interval quotaInterval,
        final Instant closureStart
    ) {
        final Map<Species, Double> intervalQuotas = quotas.get(quotaInterval);
        if (intervalQuotas == null || intervalQuotas.isEmpty()) return;
        final Map<Species, Double> intervalCatches = catches.get(quotaInterval);
        if (intervalCatches == null || intervalCatches.isEmpty()) return;
        final boolean anyQuotaReached = intervalQuotas
            .entrySet()
            .stream()
            .anyMatch(entry ->
                intervalCatches.getOrDefault(entry.getKey(), 0.0) >= entry.getValue()
            );
        if (anyQuotaReached && !closureInstants.containsKey(quotaInterval)) {
            closureInstants.put(quotaInterval, closureStart);
        }
    }
}
