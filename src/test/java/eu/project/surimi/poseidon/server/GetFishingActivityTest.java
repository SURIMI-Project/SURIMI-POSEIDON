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

import build.buf.gen.surimi.v1.FishingActivity;
import build.buf.gen.surimi.v1.GetFishingActivityRequest;
import build.buf.gen.surimi.v1.FisheryServiceGrpc;
import eu.project.surimi.poseidon.regulations.TotalAllowableCatchQuotas;
import eu.project.surimi.poseidon.scenarios.TacOnlyScenario;
import eu.project.surimi.poseidon.server.fleet.FleetSegment;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.agents.regulations.actions.ExtendedFishingAction;
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
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetFishingActivityTest extends ServiceTest {

    private static final Species COD = new Species("COD", null, null);
    private static final LocalDateTime START = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime END = START.plusDays(31);
    private static final Interval INTERVAL = Interval.of(START.toInstant(UTC), END.toInstant(UTC));
    private static final FleetSegment OTB_ESP_SEGMENT =
        new FleetSegment("OTB", null, "Industrial", "ESP", "POSEIDON");
    private static final FleetSegment PS_FRA_SEGMENT =
        new FleetSegment("PS", null, "Industrial", "FRA", "POSEIDON");

    GetFishingActivityTest() {
        super(TacOnlyScenario.class);
    }

    @Test
    void getFishingActivityRejectsMissingStartDateTime() {
        final String simulationId = initialiseSimulation();

        final Throwable thrown = catchThrowable(() ->
            regulationsStub().getFishingActivity(
                GetFishingActivityRequest.newBuilder()
                    .setSimulationId(simulationId)
                    .setEndDateTime(toTimestamp(END))
                    .build()
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getFishingActivityRejectsMissingEndDateTime() {
        final String simulationId = initialiseSimulation();

        final Throwable thrown = catchThrowable(() ->
            regulationsStub().getFishingActivity(
                GetFishingActivityRequest.newBuilder()
                    .setSimulationId(simulationId)
                    .setStartDateTime(toTimestamp(START))
                    .build()
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getFishingActivityRejectsEndDateTimeBeforeStartDateTime() {
        final String simulationId = initialiseSimulation();

        final Throwable thrown = catchThrowable(() ->
            regulationsStub().getFishingActivity(
                GetFishingActivityRequest.newBuilder()
                    .setSimulationId(simulationId)
                    .setStartDateTime(toTimestamp(END))
                    .setEndDateTime(toTimestamp(START))
                    .build()
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getFishingActivityRejectsEmptyQueryInterval() {
        final String simulationId = initialiseSimulation();

        final Throwable thrown = catchThrowable(() ->
            regulationsStub().getFishingActivity(
                GetFishingActivityRequest.newBuilder()
                    .setSimulationId(simulationId)
                    .setStartDateTime(toTimestamp(START))
                    .setEndDateTime(toTimestamp(START))
                    .build()
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void getFishingActivityReturnsEmptySummaryWhenNoTacsOverlapQueryInterval() {
        final String simulationId = initialiseSimulation();

        final var response = getFishingActivity(simulationId, START, END);

        assertThat(response.hasFishingActivitySummary()).isTrue();
        assertThat(response.getFishingActivitySummary().getFishingActivitiesList()).isEmpty();
    }

    @Test
    void getFishingActivityReturnsFullRatioWhenFleetSegmentHasNoClosure() {
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        tac.setQuota(INTERVAL, OTB_ESP_SEGMENT, COD, 100.0);

        final var response = getFishingActivity(simulationId, START, END);

        assertThat(response.getFishingActivitySummary().getFishingActivitiesList())
            .containsExactly(
                FishingActivity.newBuilder()
                    .setFleetSegment(toProtoFleetSegment(OTB_ESP_SEGMENT))
                    .setFishingActivityRatio(1.0)
                    .build()
            );
    }

    @Test
    void getFishingActivityReturnsPartialRatioWhenClosureStartsInsideQueryInterval() {
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);
        final Simulation simulation = simulationManager.getSimulation(simulationId);
        final LocalDateTime closureStart = START.plusDays(10);

        tac.setQuota(INTERVAL, OTB_ESP_SEGMENT, COD, 100.0);
        broadcastEvent(simulation, vessel("OTB", "ESP", 18.5), closureStart, COD, 100.0);
        tac.step(simulation);

        final var response = getFishingActivity(simulationId, START, END);

        assertThat(response.getFishingActivitySummary().getFishingActivitiesList())
            .containsExactly(
                FishingActivity.newBuilder()
                    .setFleetSegment(toProtoFleetSegment(OTB_ESP_SEGMENT))
                    .setFishingActivityRatio(11.0 / 31.0)
                    .build()
            );
    }

    @Test
    void getFishingActivityReturnsZeroRatioWhenClosurePredatesQueryInterval() {
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);
        final Simulation simulation = simulationManager.getSimulation(simulationId);
        final LocalDateTime closureStart = START.plusDays(5);
        final LocalDateTime queryStart = START.plusDays(10);
        final LocalDateTime queryEnd = END;

        tac.setQuota(INTERVAL, OTB_ESP_SEGMENT, COD, 100.0);
        broadcastEvent(simulation, vessel("OTB", "ESP", 18.5), closureStart, COD, 100.0);
        tac.step(simulation);

        final var response = getFishingActivity(simulationId, queryStart, queryEnd);

        assertThat(response.getFishingActivitySummary().getFishingActivitiesList())
            .containsExactly(
                FishingActivity.newBuilder()
                    .setFleetSegment(toProtoFleetSegment(OTB_ESP_SEGMENT))
                    .setFishingActivityRatio(0.0)
                    .build()
            );
    }

    @Test
    void getFishingActivityReturnsOneEntryPerFleetSegmentWithTacInQueryInterval() {
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        tac.setQuota(INTERVAL, OTB_ESP_SEGMENT, COD, 100.0);
        tac.setQuota(INTERVAL, PS_FRA_SEGMENT, COD, 100.0);

        final var response = getFishingActivity(simulationId, START, END);

        assertThat(response.getFishingActivitySummary().getFishingActivitiesList())
            .containsExactlyInAnyOrder(
                FishingActivity.newBuilder()
                    .setFleetSegment(toProtoFleetSegment(OTB_ESP_SEGMENT))
                    .setFishingActivityRatio(1.0)
                    .build(),
                FishingActivity.newBuilder()
                    .setFleetSegment(toProtoFleetSegment(PS_FRA_SEGMENT))
                    .setFishingActivityRatio(1.0)
                    .build()
            );
    }

    @Test
    void getFishingActivityIgnoresFleetSegmentsWithTacOutsideQueryInterval() {
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);
        final Interval laterInterval =
            Interval.of(END.plusDays(1).toInstant(UTC), END.plusDays(32).toInstant(UTC));

        tac.setQuota(INTERVAL, OTB_ESP_SEGMENT, COD, 100.0);
        tac.setQuota(laterInterval, PS_FRA_SEGMENT, COD, 100.0);

        final var response = getFishingActivity(simulationId, START, END);

        assertThat(response.getFishingActivitySummary().getFishingActivitiesList())
            .containsExactly(
                FishingActivity.newBuilder()
                    .setFleetSegment(toProtoFleetSegment(OTB_ESP_SEGMENT))
                    .setFishingActivityRatio(1.0)
                    .build()
            );
    }

    private build.buf.gen.surimi.v1.GetFishingActivityResponse getFishingActivity(
        final String simulationId,
        final LocalDateTime start,
        final LocalDateTime end
    ) {
        return regulationsStub().getFishingActivity(
            GetFishingActivityRequest.newBuilder()
                .setSimulationId(simulationId)
                .setStartDateTime(toTimestamp(start))
                .setEndDateTime(toTimestamp(end))
                .build()
        );
    }

    private FisheryServiceGrpc.FisheryServiceBlockingStub regulationsStub() {
        return FisheryServiceGrpc.newBlockingStub(channel);
    }

    private TotalAllowableCatchQuotas getTac(final String simulationId) {
        return simulationManager
            .getSimulation(simulationId)
            .getComponent(TotalAllowableCatchQuotas.class);
    }

    private void broadcastEvent(
        final Simulation simulation,
        final Vessel vessel,
        final LocalDateTime dateTime,
        final Species species,
        final double biomassInKg
    ) {
        final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
        when(action.getAgent()).thenReturn(vessel);
        when(action.getStartDateTime()).thenReturn(dateTime);
        final FishingOutcome outcome = new FishingOutcome(Bucket.of(species, biomassInKg), null);
        simulation.getEventManager().broadcast(new FishingEvent(action, outcome));
        simulation.getTemporalSchedule().stepUntil(simulation, dateTime.plusDays(1));
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
}
