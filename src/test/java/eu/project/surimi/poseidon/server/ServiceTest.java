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
import eu.project.surimi.poseidon.scenarios.MinimalScenarioFile;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static java.lang.System.Logger.Level.INFO;

public abstract class ServiceTest {

    private static final String SCENARIO_ID = "minimal";
    private static final String STEP_SIZE = "P1M";
    private static final LocalDateTime START_DATE_TIME =
        LocalDate.of(2000, 1, 1).atStartOfDay();
    private static final System.Logger logger =
        System.getLogger(WorkflowServiceTest.class.getName());
    private static final int PORT = 50051;
    io.grpc.Server server;
    WorkflowServiceGrpc.WorkflowServiceBlockingStub workflowStub;
    EcologyServiceGrpc.EcologyServiceBlockingStub ecologyStub;
    FisheryServiceGrpc.FisheryServiceBlockingStub fisheryStub;
    MarketServiceGrpc.MarketServiceBlockingStub marketStub;

    @BeforeEach
    void setUp() {
        try {
            server = new Server(MinimalScenarioFile.getPath(), PORT).startServer();
            final ChannelCredentials credentials = InsecureChannelCredentials.create();
            final ManagedChannel channel =
                Grpc.newChannelBuilder("localhost:" + PORT, credentials).build();
            workflowStub = WorkflowServiceGrpc.newBlockingStub(channel);
            ecologyStub = EcologyServiceGrpc.newBlockingStub(channel);
            fisheryStub = FisheryServiceGrpc.newBlockingStub(channel);
            marketStub = MarketServiceGrpc.newBlockingStub(channel);
        } catch (final InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        logger.log(INFO, "Shutting down server");
        server.shutdown();
    }

    protected InitialiseResponse initialiseSimulation(final String simulationId) {
        return workflowStub.initialise(
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
