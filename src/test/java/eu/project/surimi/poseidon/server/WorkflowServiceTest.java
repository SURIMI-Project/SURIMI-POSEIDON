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
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowServiceTest extends ServiceTest {

    @Test
    void simulationCanBeStarted() {
        final String simulationId = UUID.randomUUID().toString();
        final InitialiseResponse response = initialiseSimulation(simulationId);
        assertEquals(simulationId, response.getSimulationId());
    }

    @Test
    void cantUseSameIdTwice() {
        final String simulationId = UUID.randomUUID().toString();
        initialiseSimulation(simulationId);
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

    @Test
    void idBecomesAvailableAfterCancelling() {
        final String simulationId = UUID.randomUUID().toString();
        initialiseSimulation(simulationId);
        final CancelResponse cancelResponse = client.cancel(
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
        final String simulationId = UUID.randomUUID().toString();
        initialiseSimulation(simulationId);
        final FinaliseResponse cancelResponse = client.finalise(
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
        final String simulationId = UUID.randomUUID().toString();
        initialiseSimulation(simulationId);
        final SimulateStepRequest simulateStepRequest = SimulateStepRequest
            .newBuilder()
            .setSimulationId(simulationId)
            .build();
        final SimulateStepResponse response1 = client.simulateStep(simulateStepRequest);
        assertEquals(simulationId, response1.getSimulationId());
        final SimulateStepResponse response2 = client.simulateStep(simulateStepRequest);
        assertEquals(simulationId, response2.getSimulationId());
    }
}
