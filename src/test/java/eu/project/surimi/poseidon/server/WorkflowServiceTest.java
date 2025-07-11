/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025, University of Oxford.
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

import build.buf.gen.surimi.v1.*;
import eu.project.surimi.poseidon.scenarios.MinimalScenario;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowServiceTest extends ServiceTest {

    public WorkflowServiceTest() {
        super(MinimalScenario.class);
    }

    @Test
    void simulationCanBeStarted() {
        final String simulationId = UUID.randomUUID().toString();
        final InitialiseResponse response = initialiseSimulation(simulationId);
        assertEquals(simulationId, response.getSimulationId());
    }

    @Test
    void cantUseSameIdTwice() {
        final String simulationId = initialiseSimulation();
        assertThrows(StatusRuntimeException.class, () -> initialiseSimulation(simulationId));
    }

    @Test
    void canStartTwoSimulationsWithDifferentIds() {
        final String simulationId1 = UUID.randomUUID().toString();
        final String simulationId2 = UUID.randomUUID().toString();
        final InitialiseResponse response1 = initialiseSimulation(simulationId1);
        assertEquals(simulationId1, response1.getSimulationId());
        final InitialiseResponse response2 = initialiseSimulation(simulationId2);
        assertEquals(simulationId2, response2.getSimulationId());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void canStartAndStepManySimulations() {
        final int numSimulations = 100;
        final List<String> simulations = Stream
            .generate(this::initialiseSimulation)
            .limit(numSimulations)
            .toList();
        simulations.forEach(simulationId ->
            workflowStub.simulateStep(
                SimulateStepRequest
                    .newBuilder()
                    .setSimulationId(simulationId)
                    .build()
            )
        );
    }

    @Test
    void idBecomesAvailableAfterCancelling() {
        final String simulationId = initialiseSimulation();
        final CancelResponse cancelResponse = workflowStub.cancel(
            CancelRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .build()
        );
        assertEquals(simulationId, cancelResponse.getSimulationId());
        final InitialiseResponse initResponse = initialiseSimulation(simulationId);
        assertEquals(simulationId, initResponse.getSimulationId());
    }

    @Test
    void idBecomesAvailableAfterFinalising() {
        final String simulationId = initialiseSimulation();
        final FinaliseResponse cancelResponse = workflowStub.finalise(
            FinaliseRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .build()
        );
        assertEquals(simulationId, cancelResponse.getSimulationId());
        final InitialiseResponse initResponse = initialiseSimulation(simulationId);
        assertEquals(simulationId, initResponse.getSimulationId());
    }

    @Test
    void canStep() {
        final String simulationId = initialiseSimulation();
        final SimulateStepRequest simulateStepRequest = SimulateStepRequest
            .newBuilder()
            .setSimulationId(simulationId)
            .build();
        final SimulateStepResponse response1 = workflowStub.simulateStep(simulateStepRequest);
        assertEquals(simulationId, response1.getSimulationId());
        final SimulateStepResponse response2 = workflowStub.simulateStep(simulateStepRequest);
        assertEquals(simulationId, response2.getSimulationId());
    }
}
