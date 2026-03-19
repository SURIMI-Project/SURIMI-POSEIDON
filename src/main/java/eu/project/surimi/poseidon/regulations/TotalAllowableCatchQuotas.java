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

import eu.project.surimi.poseidon.server.fleet.FleetSegment;
import eu.project.surimi.poseidon.server.fleet.FleetSegmentMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.threeten.extra.Interval;
import sim.engine.SimState;
import sim.engine.Steppable;
import uk.ac.ox.poseidon.agents.regulations.actions.TemporalFishingAction;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEventAccumulator;
import uk.ac.ox.poseidon.biology.buckets.Bucket;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.regulations.Regulations;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

/**
 * Total Allowable Catch (TAC) regulation defined by interval, fleet segment, and species.
 * <p>
 * This regulation consumes {@link uk.ac.ox.poseidon.agents.tasks.fishing.FishingEvent}s and
 * accumulates landed biomass per fleet-quota interval, where a fleet-quota interval is a fleet
 * segment plus an interval. Fishing is permitted unless a requested fishing action overlaps a
 * closed portion of at least one fleet-quota interval that covers the acting vessel's fleet
 * segment.
 * <p>
 * This class implements {@link Steppable} and processes accumulated fishing events in batch during
 * each simulation step. Closure is determined based on the total catch recorded up to the point of
 * the {@code step} call.
 * <p>
 * Model assumptions implemented here:
 * <ul>
 *     <li>TAC accounting is limited to fleet segment, species, and time. Area and stock
 *     dimensions are intentionally not modelled here.</li>
 *     <li>{@link uk.ac.ox.poseidon.agents.tasks.fishing.FishingEvent} is the accounting
 *     trigger. Accumulated catches from the
 *     {@link FishingEventAccumulator} are processed during the {@link #step(SimState)} call.</li>
 *     <li>Both sold and unsold biomass count against TAC, as reflected in the gross catch
 *     of the {@link uk.ac.ox.poseidon.agents.tasks.fishing.FishingOutcome}.</li>
 *     <li>Closure is per fleet-quota interval: once any configured quota-species in an
 *     interval/fleet-segment combination reaches or exceeds its limit at time {@code t}, fishing
 *     is closed for that fleet-quota interval
 *     from {@code t} until the end of the interval.</li>
 *     <li>Closure is sticky. Once an interval closes, later events do not reopen it.</li>
 *     <li>A fishing event contributes to every configured fleet-quota interval whose interval
 *     contains the event timestamp and whose fleet segment covers the vessel's fleet
 *     segment.</li>
 * </ul>
 * <p>
 * Quota definition rules:
 * <ul>
 *     <li>Quota units are kilograms.</li>
 *     <li>Quota definitions are immutable per interval/fleet-segment/species combination.
 *     Re-setting the same combination is rejected.</li>
 *     <li>Overlapping TAC definitions are rejected whenever their intervals overlap and their
 *     fleet segments overlap, regardless of species.</li>
 *     <li>Within one fleet segment, all quota-species must therefore be defined on the same
 *     non-overlapping interval partition.</li>
 *     <li>Species matching for catch accounting uses {@link Species#covers(Species)} semantics,
 *     not strict equality. A generic quota such as "HKE" therefore counts staged catches such as
 *     "HKE;juvenile", provided no overlapping TAC interval already exists for that fleet
 *     segment.</li>
 * </ul>
 * <p>
 * Internal representation notes:
 * <ul>
 *     <li>Configured quotas are stored by fleet-quota interval and quota-species.</li>
 *     <li>Accumulated catches are stored by fleet-quota interval and matched quota-species.</li>
 *     <li>Closures are stored by fleet-quota interval plus the first exhaustion timestamp.
 *     Effective closed sub-intervals are derived as needed as
 *     {@code [closureStart, quotaIntervalEnd)}.</li>
 *     <li>Intervals are treated independently once defined; closure in one interval does not imply
 *     closure in another unless a fishing action overlaps both. Closures are segment-specific.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class TotalAllowableCatchQuotas implements Regulations<TemporalFishingAction>, Steppable {

    private final @NonNull FishingEventAccumulator fishingEventAccumulator;
    private final @NonNull FleetSegmentMapper fleetSegmentMapper;

    /**
     * Configured TAC quotas in kilograms, keyed by fleet-quota interval and quota-species.
     */
    private final Map<QuotaKey, Map<Species, Double>> quotas = new HashMap<>();

    /**
     * Accumulated landed catches in kilograms, keyed by fleet-quota interval and quota-species.
     * <p>
     * Keys in this map align with quota-species (not necessarily the raw sold species), because
     * catches are recorded after applying {@link Species#covers(Species)} matching.
     */
    private final Map<QuotaKey, Map<Species, Double>> catches = new HashMap<>();

    /**
     * Closure start instants keyed by fleet-quota interval.
     */
    private final Map<QuotaKey, Instant> closureInstants = new LinkedHashMap<>();

    /**
     * Returns whether a fishing action is permitted for the acting vessel's fleet segment.
     * <p>
     * The action is rejected if its interval overlaps any effective closure interval for a
     * fleet-quota interval that covers the acting vessel's fleet segment.
     *
     * @param action fishing action to evaluate
     * @return {@code true} when the action does not overlap any closed interval
     */
    @Override
    public boolean isPermitted(final TemporalFishingAction action) {
        checkNotNull(action);
        final FleetSegment vesselFleetSegment =
            fleetSegmentMapper.apply(checkNotNull(
                action.getAgent(),
                "Fishing action agent is required."
            ));
        return getEffectiveClosureIntervals(vesselFleetSegment)
            .noneMatch(closureInterval -> closureInterval.overlaps(action.getInterval()));
    }

    @Override
    public void step(final SimState simState) {
        checkArgument(simState instanceof Simulation);
        final Simulation simulation = (Simulation) simState;
        final Instant now = simulation.getTemporalSchedule().getDateTime().toInstant(UTC);
        fishingEventAccumulator
            .getEvents()
            .collect(groupingBy(
                fishingEvent -> fleetSegmentMapper.apply(fishingEvent.getAction().getAgent()),
                reducing(
                    Bucket.empty(),
                    fishingEvent -> fishingEvent.getOutcome().getGrossCatch(),
                    Bucket::add
                )
            ))
            .forEach((fleetSegment, catches) ->
                registerCatches(now, fleetSegment, catches)
            );
        fishingEventAccumulator.clear();
    }

    /**
     * Sets a quota for a given interval/fleet-segment/species combination.
     * <p>
     * Quota definitions are immutable once set for a given fleet-quota interval and species.
     * Overlapping TAC definitions are also rejected when interval and fleet-segment applicability
     * overlap, regardless of species.
     *
     * @param interval     interval to which the quota applies
     * @param fleetSegment fleet segment to which the quota applies
     * @param species      quota-species key
     * @param quotaInKg    allowed biomass in kilograms (must be non-negative)
     */
    public void setQuota(
        final Interval interval,
        final FleetSegment fleetSegment,
        final Species species,
        final double quotaInKg
    ) {
        checkArgument(quotaInKg >= 0.0, "TAC quota must be non-negative.");
        checkNotNull(interval, "TAC interval is required.");
        checkNotNull(fleetSegment, "TAC fleet segment is required.");
        checkNotNull(species, "TAC species is required.");
        final QuotaKey quotaKey =
            new QuotaKey(fleetSegment, interval);
        validateNewQuotaDefinition(quotaKey, species);
        quotas.computeIfAbsent(quotaKey, __ -> new HashMap<>()).put(species, quotaInKg);
    }

    /**
     * Returns effective closure intervals for a fleet segment.
     * <p>
     * The returned intervals are derived from recorded exhaustion timestamps for fleet-quota
     * intervals whose fleet segment covers the requested fleet segment.
     *
     * @param fleetSegment fleet segment to query
     * @return closed sub-intervals in insertion order
     */
    public Stream<Interval> getEffectiveClosureIntervals(final FleetSegment fleetSegment) {
        checkNotNull(fleetSegment, "Fleet segment is required.");
        return closureInstants
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().getFleetSegment().covers(fleetSegment))
            .map(entry -> Interval.of(entry.getValue(), entry.getKey().getInterval().getEnd()));
    }

    /**
     * Returns the proportion of a query interval during which fishing is permitted for each fleet
     * segment with TAC definitions overlapping that interval.
     *
     * @param queryInterval interval to query
     * @return permitted fishing ratio per fleet segment in sorted order
     */
    public Map<FleetSegment, Double> getFishingActivityRatios(final Interval queryInterval) {
        checkNotNull(queryInterval, "Query interval is required.");
        checkArgument(
            Duration.between(queryInterval.getStart(), queryInterval.getEnd()).getSeconds() > 0L,
            "Fishing activity query interval must be non-empty."
        );
        final Map<FleetSegment, Double> fishingActivityRatios = new LinkedHashMap<>();
        quotas
            .keySet()
            .stream()
            .filter(quotaKey -> quotaKey.getInterval().overlaps(queryInterval))
            .map(QuotaKey::getFleetSegment)
            .distinct()
            .sorted()
            .forEach(fleetSegment ->
                fishingActivityRatios.put(
                    fleetSegment,
                    permittedFishingRatio(queryInterval, fleetSegment)
                )
            );
        return fishingActivityRatios;
    }

    private void validateNewQuotaDefinition(
        final QuotaKey quotaKey,
        final Species quotaSpecies
    ) {
        for (final var quotaEntry : quotas.entrySet()) {
            final QuotaKey existingQuotaKey = quotaEntry.getKey();
            if (!existingQuotaKey
                .getInterval()
                .overlaps(quotaKey.getInterval())) continue;
            if (!existingQuotaKey
                .getFleetSegment()
                .overlaps(quotaKey.getFleetSegment())) continue;
            final Map<Species, Double> existingQuotas = quotaEntry.getValue();
            if (!existingQuotaKey.equals(quotaKey)) {
                throw new IllegalArgumentException(
                    ("Overlapping TAC interval definition for interval %s, fleet segment '%s', " +
                        "and species '%s': species quotas are already configured for overlapping " +
                        "interval %s and fleet segment '%s'.")
                        .formatted(
                            quotaKey.getInterval(),
                            quotaKey.getFleetSegment(),
                            quotaSpecies,
                            existingQuotaKey.getInterval(),
                            existingQuotaKey.getFleetSegment()
                        )
                );
            }
            if (existingQuotas.containsKey(quotaSpecies)) {
                throw new IllegalArgumentException(
                    ("Overlapping TAC definition for interval %s, fleet segment '%s', and species" +
                        " " +
                        "'%s': the same species is already configured for this fleet segment and " +
                        "interval.")
                        .formatted(
                            quotaKey.getInterval(),
                            quotaKey.getFleetSegment(),
                            quotaSpecies
                        )
                );
            }
            final Species conflictingSpecies = existingQuotas
                .keySet()
                .stream()
                .filter(existingSpecies -> quotaSpeciesOverlap(existingSpecies, quotaSpecies))
                .findFirst()
                .orElse(null);
            if (conflictingSpecies != null) {
                throw new IllegalArgumentException(
                    ("Ambiguous TAC definition for interval %s, fleet segment '%s', and species " +
                        "'%s': overlapping quota species '%s' is already configured for the same " +
                        "fleet segment and interval.")
                        .formatted(
                            quotaKey.getInterval(),
                            quotaKey.getFleetSegment(),
                            quotaSpecies,
                            conflictingSpecies
                        )
                );
            }
        }
    }

    private static boolean quotaSpeciesOverlap(
        final Species firstSpecies,
        final Species secondSpecies
    ) {
        return firstSpecies.covers(secondSpecies) || secondSpecies.covers(firstSpecies);
    }

    private void registerCatches(
        final Instant instant,
        final FleetSegment fleetSegment,
        final Bucket catchesToRegister
    ) {
        checkNotNull(instant);
        checkNotNull(fleetSegment);
        checkNotNull(catchesToRegister);
        quotas
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().getInterval().contains(instant))
            .filter(entry -> entry.getKey().getFleetSegment().covers(fleetSegment))
            .forEach(entry -> {
                final QuotaKey quotaKey = entry.getKey();
                final Map<Species, Double> quotaValues = entry.getValue();
                catchesToRegister.forEachBiomassValue((caughtSpecies, caughtBiomass) ->
                    quotaValues.forEach((quotaSpecies, quotaBiomass) -> {
                        if (quotaSpecies.covers(caughtSpecies)) {
                            final Double newCaughtBiomass = catches
                                .computeIfAbsent(quotaKey, __ -> new HashMap<>())
                                .merge(quotaSpecies, caughtBiomass, Double::sum);
                            if (newCaughtBiomass >= quotaBiomass) {
                                closureInstants.put(quotaKey, instant);
                            }
                        }
                    })
                );
            });
    }

    private double permittedFishingRatio(
        final Interval queryInterval,
        final FleetSegment fleetSegment
    ) {
        final long queryDurationSeconds =
            Duration.between(queryInterval.getStart(), queryInterval.getEnd()).getSeconds();
        final List<Interval> intervals =
            getEffectiveClosureIntervals(fleetSegment)
                .filter(closureInterval -> closureInterval.overlaps(queryInterval))
                .map(closureInterval -> closureInterval.intersection(queryInterval))
                .toList();
        final long closedDurationSeconds = intervals
            .stream()
            .mapToLong(interval -> Duration
                .between(interval.getStart(), interval.getEnd())
                .getSeconds())
            .sum();
        return (double) (queryDurationSeconds - closedDurationSeconds) / queryDurationSeconds;
    }

    @Value
    private static class QuotaKey {
        FleetSegment fleetSegment;
        Interval interval;
    }
}
