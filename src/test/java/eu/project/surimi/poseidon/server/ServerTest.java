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

import build.buf.gen.surimi.v1.InitialiseRequest;
import build.buf.gen.surimi.v1.InitialiseResponse;
import build.buf.gen.surimi.v1.WorkflowServiceGrpc;
import eu.project.surimi.poseidon.scenarios.MinimalScenarioFile;
import io.grpc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static java.lang.System.Logger.Level.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerTest {

    private static final System.Logger logger = System.getLogger(ServerTest.class.getName());

    private static final int PORT = 50051;
    private static final String SCENARIO_ID = "minimal";
    private static final String STEP_SIZE = "P1M";
    private static final LocalDateTime START_DATE_TIME =
        LocalDate.of(2000, 1, 1).atStartOfDay();

    io.grpc.Server server;
    WorkflowServiceGrpc.WorkflowServiceBlockingStub client;

    @BeforeEach
    void setUp() {
        try {
            server = new Server(MinimalScenarioFile.getPath(), PORT).startServer();
            final ChannelCredentials credentials = InsecureChannelCredentials.create();
            final ManagedChannel channel =
                Grpc.newChannelBuilder("localhost:" + PORT, credentials).build();
            client = WorkflowServiceGrpc.newBlockingStub(channel);
        } catch (final InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        logger.log(INFO, "Shutting down server");
        server.shutdown();
    }

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
        initialiseSimulation(simulationId1);
        initialiseSimulation(simulationId2);
    }

    private InitialiseResponse initialiseSimulation(final String simulationId) {
        return client.initialise(
            InitialiseRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .setScenarioId(SCENARIO_ID)
                .setStartDateTime(toTimestamp(START_DATE_TIME))
                .setStepSize(STEP_SIZE)
                .build()
        );
    }
}
