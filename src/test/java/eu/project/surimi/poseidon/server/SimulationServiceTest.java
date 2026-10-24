/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025-2026, University of Oxford.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import build.buf.gen.surimi.v1.*;
import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import eu.project.surimi.poseidon.server.fleet.FleetSegmentMapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Period;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulationServiceTest extends ServiceTest {

  public SimulationServiceTest() {
    super(MinimalScenario.class);
  }

  @Test
  void simulationCanBeStarted() {
    final String simulationId = UUID.randomUUID().toString();
    final InitialiseSimulationResponse response = initialiseSimulation(
      simulationId
    );
    assertThat(response.getSimulationId()).isEqualTo(simulationId);
  }

  @Test
  void exposesFleetSegmentMapperComponent() {
    final String simulationId = initialiseSimulation();
    assertThatCode(() ->
      simulationManager
        .getSimulation(simulationId)
        .getComponent(FleetSegmentMapper.class)
    ).doesNotThrowAnyException();
  }

  @Test
  void cantUseSameIdTwice() {
    final String simulationId = initialiseSimulation();
    assertThatThrownBy(() -> initialiseSimulation(simulationId)).isInstanceOf(
      StatusRuntimeException.class
    );
  }

  @Test
  void rejectsScenarioNameWithParentDirectoryTraversal() {
    assertThatThrownBy(() ->
      initialiseSimulation(UUID.randomUUID().toString(), "../MinimalScenario")
    ).isInstanceOfSatisfying(StatusRuntimeException.class, error ->
      assertThat(error.getStatus().getCode()).isEqualTo(
        Status.Code.INVALID_ARGUMENT
      )
    );
  }

  @Test
  void rejectsScenarioNameWithPathSeparator() {
    assertThatThrownBy(() ->
      initialiseSimulation(
        UUID.randomUUID().toString(),
        "minimal/MinimalScenario"
      )
    ).isInstanceOfSatisfying(StatusRuntimeException.class, error ->
      assertThat(error.getStatus().getCode()).isEqualTo(
        Status.Code.INVALID_ARGUMENT
      )
    );
  }

  @Test
  void acceptsContractWithSingleFleetSegment() {
    final String simulationId = UUID.randomUUID().toString();
    final InitialiseSimulationResponse response = initialiseSimulation(
      simulationId,
      MinimalScenario.class.getSimpleName(),
      contractItems()
        .clearFleetSegments()
        .addFleetSegments(
          FleetSegment.newBuilder()
            .setGearCode("G1")
            .setModel(Server.MODEL_NAME)
        )
        .build()
    );
    assertThat(response.getSimulationId()).isEqualTo(simulationId);
  }

  @Test
  void rejectsContractWithoutFleetSegments() {
    assertThatThrownBy(() ->
      initialiseSimulation(
        UUID.randomUUID().toString(),
        MinimalScenario.class.getSimpleName(),
        contractItems().clearFleetSegments().build()
      )
    ).isInstanceOfSatisfying(StatusRuntimeException.class, error ->
      assertThat(error.getStatus().getCode()).isEqualTo(
        Status.Code.INVALID_ARGUMENT
      )
    );
  }

  @Test
  void rejectsContractWithoutPoseidonFleetSegments() {
    assertThatThrownBy(() ->
      initialiseSimulation(
        UUID.randomUUID().toString(),
        MinimalScenario.class.getSimpleName(),
        contractItems()
          .clearFleetSegments()
          .addFleetSegments(
            FleetSegment.newBuilder().setGearCode("G1").setModel("EwE")
          )
          .addFleetSegments(FleetSegment.newBuilder().setGearCode("G2"))
          .build()
      )
    ).isInstanceOfSatisfying(StatusRuntimeException.class, error ->
      assertThat(error.getStatus().getCode()).isEqualTo(
        Status.Code.INVALID_ARGUMENT
      )
    );
  }

  @Test
  void rejectsContractWithOverlappingFleetSegments() {
    assertThatThrownBy(() ->
      initialiseSimulation(
        UUID.randomUUID().toString(),
        MinimalScenario.class.getSimpleName(),
        contractItems()
          .clearFleetSegments()
          .addFleetSegments(
            FleetSegment.newBuilder()
              .setGearCode("G1")
              .setModel(Server.MODEL_NAME)
          )
          .addFleetSegments(
            FleetSegment.newBuilder()
              .setGearCode("G1")
              .setCountryCode("ESP")
              .setModel(Server.MODEL_NAME)
          )
          .build()
      )
    ).isInstanceOfSatisfying(StatusRuntimeException.class, error ->
      assertThat(error.getStatus().getCode()).isEqualTo(
        Status.Code.INVALID_ARGUMENT
      )
    );
  }

  @Test
  void canStartTwoSimulationsWithDifferentIds() {
    final String simulationId1 = UUID.randomUUID().toString();
    final String simulationId2 = UUID.randomUUID().toString();
    final InitialiseSimulationResponse response1 = initialiseSimulation(
      simulationId1
    );
    assertThat(response1.getSimulationId()).isEqualTo(simulationId1);
    final InitialiseSimulationResponse response2 = initialiseSimulation(
      simulationId2
    );
    assertThat(response2.getSimulationId()).isEqualTo(simulationId2);
  }

  @Test
  void canStartAndStepManySimulations() {
    for (int i = 0; i < 25; i++) {
      step(initialiseSimulation(), START_DATE_TIME);
    }
  }

  @Test
  void idBecomesAvailableAfterCancelling() {
    final String simulationId = initialiseSimulation();
    final CancelSimulationResponse cancelResponse =
      simulationStub.cancelSimulation(
        CancelSimulationRequest.newBuilder()
          .setSimulationId(simulationId)
          .build()
      );
    assertThat(cancelResponse.getSimulationId()).isEqualTo(simulationId);
    final InitialiseSimulationResponse initResponse = initialiseSimulation(
      simulationId
    );
    assertThat(initResponse.getSimulationId()).isEqualTo(simulationId);
  }

  @Test
  void idBecomesAvailableAfterFinalising() {
    final String simulationId = initialiseSimulation();
    final FinaliseSimulationResponse cancelResponse =
      simulationStub.finaliseSimulation(
        FinaliseSimulationRequest.newBuilder()
          .setSimulationId(simulationId)
          .build()
      );
    assertThat(cancelResponse.getSimulationId()).isEqualTo(simulationId);
    final InitialiseSimulationResponse initResponse = initialiseSimulation(
      simulationId
    );
    assertThat(initResponse.getSimulationId()).isEqualTo(simulationId);
  }

  @Test
  void canStep() {
    final String simulationId = initialiseSimulation();
    final SimulateStepResponse response1 = step(simulationId, START_DATE_TIME);
    assertThat(response1.getSimulationId()).isEqualTo(simulationId);
    final SimulateStepResponse response2 = step(
      simulationId,
      START_DATE_TIME.plus(Period.parse(STEP_SIZE))
    );
    assertThat(response2.getSimulationId()).isEqualTo(simulationId);
  }
}
