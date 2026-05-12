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
import eu.project.surimi.poseidon.scenarios.TacOnlyScenario;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.biology.species.Species;

import java.time.LocalDateTime;

import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toProtoSpecies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class UpdateRegulationsValidationTest extends ServiceTest {

    UpdateRegulationsValidationTest() {
        super(TacOnlyScenario.class);
    }

    @Test
    void updateRegulationsRejectsIncompleteRequest() {
        final String simulationId = initialiseSimulation();
        final var regulationsStub =
            FisheryServiceGrpc.newBlockingStub(channel);

        final Throwable thrown = catchThrowable(() ->
            regulationsStub.updateRegulations(
                UpdateRegulationsRequest
                    .newBuilder()
                    .setSimulationId(simulationId)
                    .build()
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("Update regulations request is missing a start date time.");
    }

    @Test
    void updateRegulationsRejectsEndDateTimeBeforeStartDateTime() {
        final String simulationId = initialiseSimulation();
        final var regulationsStub = FisheryServiceGrpc.newBlockingStub(channel);
        final LocalDateTime start = LocalDateTime.of(2000, 2, 1, 0, 0);
        final LocalDateTime end = start.minusDays(1);

        final Throwable thrown = catchThrowable(() ->
            regulationsStub.updateRegulations(
                UpdateRegulationsRequest.newBuilder()
                    .setSimulationId(simulationId)
                    .setStartDateTime(toTimestamp(start))
                    .setEndDateTime(toTimestamp(end))
                    .setRegulationsSummary(RegulationsSummary.newBuilder().build())
                    .build()
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("End date time must be on or after start date time.");
    }

    @Test
    void updateRegulationsRejectsTacEntryWithoutSpecies() {
        final String simulationId = initialiseSimulation();
        final var regulationsStub = FisheryServiceGrpc.newBlockingStub(channel);
        final LocalDateTime start = LocalDateTime.of(2000, 1, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);

        final Throwable thrown = catchThrowable(() ->
            regulationsStub.updateRegulations(
                UpdateRegulationsRequest.newBuilder()
                    .setSimulationId(simulationId)
                    .setStartDateTime(toTimestamp(start))
                    .setEndDateTime(toTimestamp(end))
                    .setRegulationsSummary(
                        RegulationsSummary.newBuilder()
                            .addTotalAllowableCatches(
                                TotalAllowableCatch.newBuilder()
                                    .setCatch(100.0)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("TAC entry is missing a species.");
    }

    @Test
    void updateRegulationsRejectsTacEntryWithoutFleetSegment() {
        final String simulationId = initialiseSimulation();
        final var regulationsStub = FisheryServiceGrpc.newBlockingStub(channel);
        final LocalDateTime start = LocalDateTime.of(2000, 1, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);

        final Throwable thrown = catchThrowable(() ->
            regulationsStub.updateRegulations(
                UpdateRegulationsRequest.newBuilder()
                    .setSimulationId(simulationId)
                    .setStartDateTime(toTimestamp(start))
                    .setEndDateTime(toTimestamp(end))
                    .setRegulationsSummary(
                        RegulationsSummary.newBuilder()
                            .addTotalAllowableCatches(
                                TotalAllowableCatch.newBuilder()
                                    .setSpecies(toProtoSpecies(new Species("COD", null, null)))
                                    .setCatch(100.0)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("TAC entry is missing a fleet segment.");
    }
}
