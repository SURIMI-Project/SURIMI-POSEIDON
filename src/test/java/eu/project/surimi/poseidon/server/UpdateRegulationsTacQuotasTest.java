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

package eu.project.surimi.poseidon.server;

import build.buf.gen.surimi.v1.FisheryServiceGrpc;
import build.buf.gen.surimi.v1.RegulationsSummary;
import build.buf.gen.surimi.v1.TotalAllowableCatch;
import build.buf.gen.surimi.v1.UpdateRegulationsRequest;
import eu.project.surimi.poseidon.regulations.TotalAllowableCatchQuotas;
import eu.project.surimi.poseidon.scenarios.TacOnlyScenario;
import eu.project.surimi.poseidon.server.fleet.FleetSegment;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.agents.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.agents.regulations.actions.TemporalFishingAction;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEvent;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingOutcome;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.biology.buckets.Bucket;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;

import java.time.LocalDateTime;
import java.util.Optional;

import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static eu.project.surimi.poseidon.server.mappers.FleetSegmentProtoMapper.toProtoFleetSegment;
import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toProtoSpecies;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateRegulationsTacQuotasTest extends ServiceTest {

    private static final Species COD = new Species("COD", null, null);
    private static final Species HADDOCK = new Species("HAD", null, null);
    private static final Species HAKE_JUVENILE = new Species("HKE", "juvenile", null);
    private static final LocalDateTime START = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime END = START.plusDays(31);
    private static final Interval INTERVAL = Interval.of(START.toInstant(UTC), END.toInstant(UTC));
    private static final double COD_QUOTA = 100.0;
    private static final FleetSegment POSEIDON_FLEET_SEGMENT =
        new FleetSegment("OTB", null, "Industrial", "ESP", "POSEIDON");
    private static final FleetSegment WILDCARD_COUNTRY_AND_LENGTH_SEGMENT =
        new FleetSegment("OTB", null, "Industrial", null, "POSEIDON");

    UpdateRegulationsTacQuotasTest() {
        super(TacOnlyScenario.class);
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

    @Test
    void updateRegulationsAppliesTacQuotasToSimulation() {
        // Verifies the API wires a TAC quota into the simulation and closes on matching catch.
        // Start a minimal simulation that only includes the TAC regulation component.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        // Send a regulation update defining a COD quota for the requested time window.
        updateQuota(simulationId, COD, COD_QUOTA);

        // Emit a matching catch and verify that the TAC component closes the interval.
        broadcastFishingEvent(simulationId, START.plusDays(1), COD, COD_QUOTA);

        assertIntervalClosed(tac, INTERVAL);
    }

    @Test
    void updateRegulationsAppliesMultipleTacEntriesFromOneRequest() {
        // Verifies one gRPC request can install multiple TAC species quotas in the same interval.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        updateQuotas(
            simulationId,
            new QuotaEntry(COD, 100.0),
            new QuotaEntry(HADDOCK, 50.0)
        );

        broadcastFishingEvent(simulationId, START.plusDays(1), COD, 99.9);
        assertIntervalOpen(tac, INTERVAL);

        broadcastFishingEvent(simulationId, START.plusDays(2), HADDOCK, 50.0);
        assertIntervalClosed(tac, INTERVAL);
    }

    @Test
    void updateRegulationsMapsLifeStageSpeciesFromRequestPayload() {
        // Verifies request species payloads preserve life-stage data when installing TAC quotas.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        updateQuota(simulationId, HAKE_JUVENILE, 50.0);

        broadcastFishingEvent(simulationId, START.plusDays(1), HAKE_JUVENILE, 50.0);
        assertIntervalClosed(tac, INTERVAL);
    }

    @Test
    void updateRegulationsRejectsOverlappingQuotaDefinitionForSameSpecies() {
        // Verifies gRPC updates reject re-defining the same species in overlapping TAC definitions.
        final String simulationId = initialiseSimulation();
        updateQuota(simulationId, COD, 100.0);

        final Throwable thrown = catchThrowable(() ->
            updateQuota(simulationId, COD, 200.0)
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("Overlapping TAC definition")
            .contains("species 'COD'");
    }

    @Test
    void updateRegulationsRejectsOverlappingQuotaSpeciesWithinOverlappingIntervals() {
        // Verifies gRPC updates reject overlapping TAC intervals within one fleet segment.
        final String simulationId = initialiseSimulation();
        final Species hakeAllStages = new Species("HKE", null, null);
        final Species hakeJuvenile = new Species("HKE", "juvenile", null);

        final Throwable thrown = catchThrowable(() ->
            updateQuotas(
                simulationId,
                new QuotaEntry(hakeAllStages, 100.0),
                new QuotaEntry(hakeJuvenile, 50.0)
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("Ambiguous TAC definition")
            .contains("species 'HKE (juvenile)'")
            .contains("quota species 'HKE'");
    }

    @Test
    void updateRegulationsRejectsOverlappingFleetSegmentsForSameSpeciesInOverlappingIntervals() {
        final String simulationId = initialiseSimulation();

        final Throwable thrown = catchThrowable(() ->
            updateQuotas(
                simulationId,
                new QuotaEntry(COD, 100.0, WILDCARD_COUNTRY_AND_LENGTH_SEGMENT),
                new QuotaEntry(COD, 50.0, POSEIDON_FLEET_SEGMENT)
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("Overlapping TAC interval definition")
            .contains("species 'COD'");
    }

    @Test
    void updateRegulationsKeepsNonOverlappingIntervalsIndependent() {
        // Verifies separate requests for disjoint intervals do not interfere with each other.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);
        final LocalDateTime laterStart = END.plusDays(1);
        final LocalDateTime laterEnd = laterStart.plusDays(31);
        final Interval laterInterval = Interval.of(
            laterStart.toInstant(UTC),
            laterEnd.toInstant(UTC)
        );

        updateQuota(simulationId, START, END, COD, 100.0);
        updateQuota(simulationId, laterStart, laterEnd, COD, 100.0);

        broadcastFishingEvent(simulationId, START.plusDays(1), COD, 100.0);

        assertIntervalClosed(tac, INTERVAL);
        assertIntervalOpen(tac, laterInterval);
    }

    @Test
    void updateRegulationsRejectsOverlappingIntervalsForSameFleetSegmentAndSpecies() {
        final String simulationId = initialiseSimulation();
        final LocalDateTime overlapStart = START.plusDays(10);
        final LocalDateTime overlapEnd = END.plusDays(10);

        updateQuota(simulationId, START, END, COD, 100.0);

        final Throwable thrown = catchThrowable(() ->
            updateQuota(simulationId, overlapStart, overlapEnd, COD, 100.0)
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("Overlapping TAC interval definition")
            .contains("species 'COD'");
    }

    @Test
    void updateRegulationsOnlyClosesMatchingFleetSegment() {
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        updateQuota(simulationId, COD, COD_QUOTA);
        broadcastFishingEvent(
            simulationId,
            vessel("PS", "FRA", 13.0),
            START.plusDays(1),
            COD,
            COD_QUOTA
        );

        assertIntervalOpen(tac, INTERVAL);
        assertThat(tac.getEffectiveClosureIntervals(POSEIDON_FLEET_SEGMENT)).isEmpty();
    }

    @Test
    void updateRegulationsTreatsBlankFleetSegmentFieldsAsWildcards() {
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);
        final Vessel otbFra = vessel("OTB", "FRA", 13.0);

        updateQuotas(
            simulationId,
            new QuotaEntry(COD, COD_QUOTA, WILDCARD_COUNTRY_AND_LENGTH_SEGMENT)
        );
        broadcastFishingEvent(simulationId, otbFra, START.plusDays(1), COD, COD_QUOTA);

        assertThat(tac.getEffectiveClosureIntervals(quotaSegment(otbFra)))
            .containsExactly(Interval.of(START.plusDays(2).toInstant(UTC), END.toInstant(UTC)));
        assertThat(tac.isPermitted(action(otbFra, INTERVAL))).isFalse();
    }

    private void updateQuota(
        final String simulationId,
        final Species species,
        final double quotaInKg
    ) {
        updateQuota(simulationId, START, END, species, quotaInKg);
    }

    private void updateQuota(
        final String simulationId,
        final LocalDateTime start,
        final LocalDateTime end,
        final Species species,
        final double quotaInKg
    ) {
        updateQuotas(
            simulationId,
            start,
            end,
            new QuotaEntry(species, quotaInKg, POSEIDON_FLEET_SEGMENT)
        );
    }

    private void updateQuotas(
        final String simulationId,
        final QuotaEntry... quotaEntries
    ) {
        FisheryServiceGrpc.newBlockingStub(channel).updateRegulations(
            updateQuotaRequest(simulationId, START, END, quotaEntries)
        );
    }

    private void updateQuotas(
        final String simulationId,
        final LocalDateTime start,
        final LocalDateTime end,
        final QuotaEntry... quotaEntries
    ) {
        FisheryServiceGrpc.newBlockingStub(channel).updateRegulations(
            updateQuotaRequest(simulationId, start, end, quotaEntries)
        );
    }

    private void broadcastFishingEvent(
        final String simulationId,
        final LocalDateTime dateTime,
        final Species species,
        final double biomassInKg
    ) {
        broadcastFishingEvent(simulationId, vessel(), dateTime, species, biomassInKg);
    }

    private void broadcastFishingEvent(
        final String simulationId,
        final Vessel vessel,
        final LocalDateTime dateTime,
        final Species species,
        final double biomassInKg
    ) {
        final Simulation simulation = simulationManager.getSimulation(simulationId);
        final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
        when(action.getAgent()).thenReturn(vessel);
        when(action.getStartDateTime()).thenReturn(dateTime);

        simulation.getEventManager().broadcast(
            new FishingEvent(
                action,
                new FishingOutcome(Bucket.of(species, biomassInKg), null)
            )
        );
        simulation.getTemporalSchedule().stepUntil(simulation, dateTime.plusDays(1));
        simulation.getComponent(TotalAllowableCatchQuotas.class).step(simulation);
    }

    private static void assertIntervalOpen(
        final TotalAllowableCatchQuotas tac,
        final Interval interval
    ) {
        assertThat(tac.isPermitted(action(vessel(), interval))).isTrue();
    }

    private static void assertIntervalClosed(
        final TotalAllowableCatchQuotas tac,
        final Interval interval
    ) {
        assertThat(tac.isPermitted(action(vessel(), interval))).isFalse();
    }

    private static UpdateRegulationsRequest updateQuotaRequest(
        final String simulationId,
        final LocalDateTime start,
        final LocalDateTime end,
        final QuotaEntry... quotaEntries
    ) {
        final var regulationsSummary = RegulationsSummary.newBuilder();
        for (final QuotaEntry quotaEntry : quotaEntries) {
            regulationsSummary.addTotalAllowableCatches(
                TotalAllowableCatch.newBuilder()
                    .setFleetSegment(toProtoFleetSegment(quotaEntry.fleetSegment()))
                    .setSpecies(toProtoSpecies(quotaEntry.species()))
                    .setCatch(quotaEntry.quotaInKg())
                    .build()
            );
        }
        return UpdateRegulationsRequest.newBuilder()
            .setSimulationId(simulationId)
            .setStartDateTime(toTimestamp(start))
            .setEndDateTime(toTimestamp(end))
            .setRegulationsSummary(regulationsSummary.build())
            .build();
    }

    private record QuotaEntry(Species species, double quotaInKg, FleetSegment fleetSegment) {
        private QuotaEntry(
            final Species species,
            final double quotaInKg
        ) {
            this(species, quotaInKg, POSEIDON_FLEET_SEGMENT);
        }
    }

    private TotalAllowableCatchQuotas getTac(final String simulationId) {
        return simulationManager
            .getSimulation(simulationId)
            .getComponent(TotalAllowableCatchQuotas.class);
    }

    private static Vessel vessel() {
        return vessel("OTB", "ESP", 18.5);
    }

    private static Vessel vessel(
        final String gearCode,
        final String countryCode,
        final double vesselLength
    ) {
        final Vessel vessel = mock(Vessel.class);
        final Gear gear = mock(Gear.class);
        when(vessel.getGear()).thenReturn(gear);
        when(gear.getCode()).thenReturn(gearCode);
        when(vessel.getTag("country_of_registration")).thenReturn(Optional.of(countryCode));
        when(vessel.getTag("loa")).thenReturn(Optional.of(vesselLength));
        return vessel;
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
}
