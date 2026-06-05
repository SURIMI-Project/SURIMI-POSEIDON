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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.agents.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.agents.regulations.actions.TemporalFishingAction;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEvent;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEventAccumulator;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingOutcome;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.biology.buckets.Bucket;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.functions.NumericIntervalMapper;
import uk.ac.ox.poseidon.core.schedule.TemporalSchedule;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotalAllowableCatchQuotasTest {

    private static final FleetSegmentMapper FLEET_SEGMENT_MAPPER =
        new FleetSegmentMapper(
            "country_of_registration",
            "loa",
            new NumericIntervalMapper<>(List.of(
                new NumericIntervalMapper.Interval<>(12.0, 18.0, "VL1218"),
                new NumericIntervalMapper.Interval<>(18.0, 24.0, "VL1824")
            )),
            "Industrial",
            "POSEIDON"
        );
    private static final FleetSegment BROAD_OTB_ESP_SEGMENT =
        new FleetSegment("OTB", null, "Industrial", "ESP", "POSEIDON");

    private FishingEventAccumulator fishingEventAccumulator;
    private TotalAllowableCatchQuotas tac;
    private Simulation simulation;
    private TemporalSchedule temporalSchedule;
    private List<FishingEvent> eventList;

    @BeforeEach
    void setUp() {
        fishingEventAccumulator = mock(FishingEventAccumulator.class);
        tac = new TotalAllowableCatchQuotas(fishingEventAccumulator, FLEET_SEGMENT_MAPPER);
        simulation = mock(Simulation.class);
        temporalSchedule = mock(TemporalSchedule.class);
        when(simulation.getTemporalSchedule()).thenReturn(temporalSchedule);
        eventList = new ArrayList<>();
        when(fishingEventAccumulator.getEvents()).thenAnswer(invocation -> eventList.stream());
    }

    private void stepAt(final LocalDateTime dateTime) {
        when(temporalSchedule.getDateTime()).thenReturn(dateTime);
        tac.step(simulation);
        eventList.clear();
    }

    private void recordEvent(
        final LocalDateTime dateTime,
        final Species species,
        final double weightInKg
    ) {
        recordEvent(vessel(), dateTime, species, weightInKg);
    }

    private void recordEvent(
        final Vessel vessel,
        final LocalDateTime dateTime,
        final Species species,
        final double weightInKg
    ) {
        final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
        when(action.getAgent()).thenReturn(vessel);
        final FishingOutcome outcome = new FishingOutcome(Bucket.of(species, weightInKg), null);
        eventList.add(new FishingEvent(action, outcome));
    }

    @Test
    void closesFisheryWhenQuotaIsReachedForExactSpecies() {
        // Verifies exact-species catch closes the interval for that fleet segment once quota is
        // reached.
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), cod, 100.0);
        recordEvent(start.plusDays(1), cod, 100.0);
        stepAt(start.plusDays(1));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void staysOpenWhenQuotaIsNotReached() {
        // Verifies sub-quota catch leaves the interval open.
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 2, 1, 0, 0);
        final LocalDateTime end = start.plusDays(28);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), cod, 100.0);
        recordEvent(start.plusDays(1), cod, 99.9);
        stepAt(start.plusDays(1));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void quotaWithoutLifeStageCoversLifeStageSpecificCatches() {
        // Verifies a generic species quota counts matching stage-specific catch.
        final Species hakeAllStages = new Species("HKE", null, null);
        final Species hakeJuvenile = new Species("HKE", "juvenile", null);
        final LocalDateTime start = LocalDateTime.of(2026, 3, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, BROAD_OTB_ESP_SEGMENT, hakeAllStages, 50.0);
        recordEvent(start.plusDays(2), hakeJuvenile, 50.0);
        stepAt(start.plusDays(2));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void quotaDoesNotCountSpeciesWithDifferentCode() {
        // Verifies quota accounting ignores catches from a different species code.
        final Species hake = new Species("HKE", null, null);
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 4, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), hake, 10.0);
        recordEvent(start.plusDays(1), cod, 100.0);
        stepAt(start.plusDays(1));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void consecutiveSalesAccumulateUntilLaterSaleClosesInterval() {
        // Verifies multiple sales accumulate and closure happens only on threshold reach.
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), cod, 100.0);

        recordEvent(start.plusDays(1), cod, 40.0);
        stepAt(start.plusDays(1));
        assertThat(tac.isPermitted(action(interval))).isTrue();

        recordEvent(start.plusDays(2), cod, 59.9);
        stepAt(start.plusDays(2));
        assertThat(tac.isPermitted(action(interval))).isTrue();

        recordEvent(start.plusDays(3), cod, 0.1);
        stepAt(start.plusDays(3));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void fisheryRemainsOpenBeforeExhaustionTimestampAndClosesFromThatTimestampOnward() {
        // Verifies effective closure starts at exhaustion time rather than interval start.
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        final LocalDateTime closureTime = start.plusDays(10);
        final LocalDateTime end = start.plusDays(31);
        final Interval quotaInterval = interval(start, end);
        final Interval beforeClosure = interval(start, closureTime);
        final Interval fromClosureOnward = interval(closureTime, end);

        tac.setQuota(quotaInterval, quotaSegment(vessel()), cod, 100.0);
        recordEvent(closureTime, cod, 100.0);
        stepAt(closureTime);

        assertThat(tac.isPermitted(action(beforeClosure))).isTrue();
        assertThat(tac.isPermitted(action(fromClosureOnward))).isFalse();
    }

    @Test
    void exhaustingEitherSpeciesQuotaClosesSharedInterval() {
        // Verifies exhausting any quota-species closes the shared interval for that fleet segment.
        final Species cod = new Species("COD", null, null);
        final Species haddock = new Species("HAD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), cod, 100.0);
        tac.setQuota(interval, quotaSegment(vessel()), haddock, 50.0);

        recordEvent(start.plusDays(1), cod, 99.9);
        stepAt(start.plusDays(1));
        assertThat(tac.isPermitted(action(interval))).isTrue();

        recordEvent(start.plusDays(2), haddock, 50.0);
        stepAt(start.plusDays(2));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void nonTargetSpeciesSalesDoNotAffectAnotherSpeciesQuota() {
        // Verifies catch only advances quotas for matching species definitions.
        final Species cod = new Species("COD", null, null);
        final Species haddock = new Species("HAD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), cod, 150.0);
        tac.setQuota(interval, quotaSegment(vessel()), haddock, 50.0);

        recordEvent(start.plusDays(1), cod, 60.0);
        stepAt(start.plusDays(1));
        recordEvent(start.plusDays(2), cod, 40.0);
        stepAt(start.plusDays(2));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void stageSpecificQuotaDoesNotCountOtherStagesWhenNotCovered() {
        // Verifies a stage-specific quota ignores other stages of the same species code.
        final Species hakeJuvenile = new Species("HKE", "juvenile", null);
        final Species hakeAdult = new Species("HKE", "adult", null);
        final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), hakeJuvenile, 50.0);
        recordEvent(start.plusDays(1), hakeAdult, 50.0);
        stepAt(start.plusDays(1));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void rejectsOverlappingQuotaSpeciesWithinOverlappingIntervals() {
        // Verifies overlapping quota species scopes are rejected within the same interval and
        // fleet segment.
        final Species hakeAllStages = new Species("HKE", null, null);
        final Species hakeJuvenile = new Species("HKE", "juvenile", null);
        final LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), hakeAllStages, 100.0);

        assertThatThrownBy(() -> tac.setQuota(interval, quotaSegment(vessel()), hakeJuvenile, 50.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ambiguous TAC definition")
            .hasMessageContaining("species 'HKE (juvenile)'")
            .hasMessageContaining("quota species 'HKE'");
    }

    @Test
    void rejectsOverlappingQuotaDefinitionForSameSpeciesInOverlappingIntervals() {
        // Verifies the same species quota cannot be defined twice in overlapping intervals.
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), cod, 100.0);

        assertThatThrownBy(() -> tac.setQuota(interval, quotaSegment(vessel()), cod, 120.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Overlapping TAC definition")
            .hasMessageContaining("species 'COD'");
    }

    @Test
    void salesOutsideConfiguredIntervalDoNotAffectClosure() {
        // Verifies only sales inside the quota interval contribute to closure.
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 12, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), cod, 100.0);
        recordEvent(start.minusSeconds(1), cod, 100.0);
        stepAt(start.minusSeconds(1));
        recordEvent(end, cod, 100.0);
        stepAt(end);

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void intervalContainsStartButNotEndTimestamp() {
        // Verifies TAC interval containment is start-inclusive and end-exclusive.
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2027, 1, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, quotaSegment(vessel()), cod, 100.0);

        recordEvent(start, cod, 100.0);
        stepAt(start);
        assertThat(tac.isPermitted(action(interval))).isFalse();

        final FishingEventAccumulator secondAccumulator = mock(FishingEventAccumulator.class);
        final TotalAllowableCatchQuotas tacEndingBoundary = new TotalAllowableCatchQuotas(
            secondAccumulator, FLEET_SEGMENT_MAPPER);
        tacEndingBoundary.setQuota(interval, quotaSegment(vessel()), cod, 100.0);

        final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
        final Vessel v = vessel();
        when(action.getAgent()).thenReturn(v);
        final FishingOutcome outcome = new FishingOutcome(Bucket.of(cod, 100.0), null);
        final FishingEvent event = new FishingEvent(action, outcome);
        when(secondAccumulator.getEvents()).thenAnswer(__ -> Stream.of(event));

        when(temporalSchedule.getDateTime()).thenReturn(end);
        tacEndingBoundary.step(simulation);

        assertThat(tacEndingBoundary.isPermitted(action(interval))).isTrue();
    }

    @Test
    void rejectsOverlappingIntervalsForSameFleetSegmentAndSpecies() {
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2027, 2, 1, 0, 0);
        final Interval earlyInterval = interval(start, start.plusDays(20));
        final Interval lateInterval = interval(start.plusDays(10), start.plusDays(31));

        tac.setQuota(earlyInterval, quotaSegment(vessel()), cod, 100.0);

        assertThatThrownBy(() -> tac.setQuota(lateInterval, quotaSegment(vessel()), cod, 100.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Overlapping TAC interval definition")
            .hasMessageContaining("species 'COD'");
    }

    @Test
    void fishingActionOverlappingClosedIntervalIsRejected() {
        // Verifies actions overlapping an effective closure interval are not permitted.
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2027, 3, 1, 0, 0);
        final Interval closedInterval = interval(start, start.plusDays(10));
        final Interval overlappingActionInterval = interval(start.plusDays(9), start.plusDays(20));

        tac.setQuota(closedInterval, quotaSegment(vessel()), cod, 100.0);
        recordEvent(start.plusDays(1), cod, 100.0);
        stepAt(start.plusDays(1));

        assertThat(tac.isPermitted(action(overlappingActionInterval))).isFalse();
    }

    @Test
    void closureForOneFleetSegmentDoesNotBlockAnotherFleetSegment() {
        final Species cod = new Species("COD", null, null);
        final Vessel otbEsp = vessel();
        final Vessel psFra = vessel("PS", "FRA", 13.0);
        final LocalDateTime start = LocalDateTime.of(2027, 4, 1, 0, 0);
        final Interval interval = interval(start, start.plusDays(30));

        tac.setQuota(interval, quotaSegment(otbEsp), cod, 100.0);
        recordEvent(otbEsp, start.plusDays(1), cod, 100.0);
        stepAt(start.plusDays(1));

        assertThat(tac.isPermitted(action(otbEsp, interval))).isFalse();
        assertThat(tac.isPermitted(action(psFra, interval))).isTrue();
    }

    @Test
    void effectiveClosureIntervalsAreFilteredByFleetSegment() {
        final Species cod = new Species("COD", null, null);
        final Vessel otbEsp = vessel();
        final Vessel psFra = vessel("PS", "FRA", 13.0);
        final LocalDateTime start = LocalDateTime.of(2027, 4, 1, 0, 0);
        final Interval interval = interval(start, start.plusDays(30));

        tac.setQuota(interval, quotaSegment(otbEsp), cod, 100.0);
        recordEvent(otbEsp, start.plusDays(1), cod, 100.0);
        stepAt(start.plusDays(1));

        assertThat(tac.getEffectiveClosureIntervals(quotaSegment(otbEsp)))
            .containsExactly(Interval.of(
                start.plusDays(1).toInstant(ZoneOffset.UTC),
                interval.getEnd()
            ));
        assertThat(tac.getEffectiveClosureIntervals(quotaSegment(psFra))).isEmpty();
    }

    @Test
    void rejectsOverlappingIntervalsForSameFleetSegmentAcrossDifferentSpecies() {
        final Species cod = new Species("COD", null, null);
        final Species haddock = new Species("HAD", null, null);
        final FleetSegment fleetSegment = quotaSegment(vessel());
        final LocalDateTime start = LocalDateTime.of(2027, 4, 1, 0, 0);
        final Interval firstInterval = interval(start, start.plusDays(20));
        final Interval secondInterval = interval(start.plusDays(10), start.plusDays(30));

        tac.setQuota(firstInterval, fleetSegment, cod, 100.0);

        assertThatThrownBy(() -> tac.setQuota(secondInterval, fleetSegment, haddock, 50.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Overlapping TAC interval definition")
            .hasMessageContaining("species 'HAD'");
    }

    @Test
    void rejectsEmptyFishingActivityQueryInterval() {
        assertThatThrownBy(() -> tac.getFishingActivityRatios(interval(
            LocalDateTime.of(2027, 4, 1, 0, 0),
            LocalDateTime.of(2027, 4, 1, 0, 0)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Fishing activity query interval must be non-empty.");
    }

    @Test
    void rejectsOverlappingFleetSegmentsForSameSpeciesInOverlappingIntervals() {
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2027, 5, 1, 0, 0);
        final Interval interval = interval(start, start.plusDays(30));

        tac.setQuota(
            interval,
            new FleetSegment("OTB", null, "Industrial", null, "POSEIDON"),
            cod,
            100.0
        );

        assertThatThrownBy(() ->
            tac.setQuota(interval, BROAD_OTB_ESP_SEGMENT, cod, 50.0)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Overlapping TAC interval definition");
    }

    @Test
    void rejectsActionWithoutAgent() {
        assertThatThrownBy(() -> tac.isPermitted(actionWithoutAgent(interval(
            LocalDateTime.of(2027, 6, 1, 0, 0),
            LocalDateTime.of(2027, 6, 2, 0, 0)
        ))))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Fishing action agent is required.");
    }

    private static Interval interval(
        final LocalDateTime start,
        final LocalDateTime end
    ) {
        return Interval.of(start.toInstant(ZoneOffset.UTC), end.toInstant(ZoneOffset.UTC));
    }

    private static TemporalFishingAction action(final Interval interval) {
        return action(vessel(), interval);
    }

    private static TemporalFishingAction action(
        final Vessel vessel,
        final Interval interval
    ) {
        return new TemporalFishingAction() {
            @Override
            public Interval getInterval() {
                return interval;
            }

            @Override
            public Vessel getAgent() {
                return vessel;
            }
        };
    }

    private static TemporalFishingAction actionWithoutAgent(final Interval interval) {
        return new TemporalFishingAction() {
            @Override
            public Interval getInterval() {
                return interval;
            }

            @Override
            public Vessel getAgent() {
                return null;
            }
        };
    }

    private static FleetSegment quotaSegment(final Vessel vessel) {
        final Gear gear = vessel.getGear();
        final String gearCode = gear == null ? null : gear.getCode();
        final Object countryCode = vessel.getTag("country_of_registration").orElse(null);
        return new FleetSegment(
            gearCode,
            null,
            "Industrial",
            countryCode == null ? null : countryCode.toString(),
            "POSEIDON"
        );
    }

    private static Vessel vessel() {
        return vessel("OTB", "ESP", 18.5);
    }

    private static Vessel vessel(
        final String gearCode,
        final String countryCode,
        final double loa
    ) {
        final Vessel vessel = mock(Vessel.class);
        final Gear gear = mock(Gear.class);
        when(vessel.getGear()).thenReturn(gear);
        when(gear.getCode()).thenReturn(gearCode);
        when(vessel.getTag("country_of_registration")).thenReturn(Optional.of(countryCode));
        when(vessel.getTag("loa")).thenReturn(Optional.of(loa));
        return vessel;
    }
}
