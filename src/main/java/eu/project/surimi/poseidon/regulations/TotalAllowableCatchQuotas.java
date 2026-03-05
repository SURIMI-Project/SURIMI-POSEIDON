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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.time.ZoneOffset.UTC;

/**
 * Total Allowable Catch (TAC) regulation with interval-based closure.
 * <p>
 * This regulation consumes {@link Sale} events and tracks cumulative landed biomass per interval
 * and per quota-species. Fishing is permitted unless the requested fishing action overlaps at least
 * one interval that has been closed due to quota exhaustion.
 * <p>
 * Closure policy implemented here is fishery-wide at interval level: if any configured
 * quota-species for an interval reaches or exceeds its quota, that whole interval is closed.
 * <p>
 * Species matching uses {@link Species#covers(Species)} semantics, not strict equality: a quota
 * configured for a species code without life stage (for example "HKE") also counts catches for
 * matching life-stage species (for example "HKE;JUV").
 * <p>
 * Notes on accounting semantics:
 * <ul>
 *     <li>Quota units are kilograms.</li>
 *     <li>Sales are mapped to intervals by sale timestamp containment.</li>
 *     <li>Both sold and unsold biomass in each {@link Sale} are counted against quotas.</li>
 *     <li>A single sold/unsold species entry may contribute to multiple quota entries if more
 *     than one quota species covers it.</li>
 *     <li>Intervals are treated independently; closures are not global across time.</li>
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
     * Intervals that are currently closed due to at least one reached/exceeded quota.
     */
    private final List<Interval> closedIntervals = new ArrayList<>();

    /**
     * Returns whether a fishing action is permitted in time.
     * <p>
     * The action is rejected if its interval overlaps any closed TAC interval.
     *
     * @param action fishing action to evaluate
     * @return {@code true} when the action does not overlap any closed interval
     */
    @Override
    public boolean isPermitted(final TemporalFishingAction action) {
        return closedIntervals.stream().noneMatch(action.getInterval()::overlaps);
    }

    /**
     * Sets or replaces a quota for a given interval/species pair.
     * <p>
     * This method also re-evaluates closure immediately. This matters when updating a quota
     * downward after catches have already accumulated.
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
        final Interval nonNullInterval = checkNotNull(interval);
        final Species nonNullSpecies = checkNotNull(species);
        quotas
            .computeIfAbsent(nonNullInterval, ignored -> new HashMap<>())
            .put(nonNullSpecies, quotaInKg);
        closeIntervalIfAnyQuotaReached(nonNullInterval);
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
        final List<Interval> relevantIntervals = quotas
            .keySet()
            .stream()
            .filter(interval -> interval.contains(saleInstant))
            .toList();
        for (final Interval interval : relevantIntervals) {
            final Map<Species, Double> intervalQuotas = quotas.get(interval);
            if (intervalQuotas == null || intervalQuotas.isEmpty()) continue;
            final Map<Species, Double> intervalCatches =
                catches.computeIfAbsent(interval, ignored -> new HashMap<>());
            for (final Sale.Item item : sale.getItems()) {
                final Species caughtSpecies = item.getSpecies();
                final double caughtKg = item.getContent().asKg();
                matchingQuotaSpecies(intervalQuotas, caughtSpecies).forEach(
                    quotaSpecies -> intervalCatches.merge(quotaSpecies, caughtKg, Double::sum)
                );
            }
            sale.getUnsold().getBuckets().values().forEach(bucket ->
                bucket.forEach((caughtSpecies, content) ->
                    matchingQuotaSpecies(intervalQuotas, caughtSpecies).forEach(
                        quotaSpecies ->
                            intervalCatches.merge(quotaSpecies, content.asKg(), Double::sum)
                    )
                )
            );
            closeIntervalIfAnyQuotaReached(interval);
        }
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
     * Closes an interval if at least one configured quota has been reached or exceeded.
     *
     * @param interval interval to evaluate
     */
    private void closeIntervalIfAnyQuotaReached(final Interval interval) {
        final Map<Species, Double> intervalQuotas = quotas.get(interval);
        if (intervalQuotas == null || intervalQuotas.isEmpty()) return;
        final Map<Species, Double> intervalCatches = catches.get(interval);
        if (intervalCatches == null || intervalCatches.isEmpty()) return;
        final boolean anyQuotaReached = intervalQuotas
            .entrySet()
            .stream()
            .anyMatch(entry ->
                intervalCatches.getOrDefault(entry.getKey(), 0.0) >= entry.getValue()
            );
        if (anyQuotaReached && !closedIntervals.contains(interval)) {
            closedIntervals.add(interval);
        }
    }
}
