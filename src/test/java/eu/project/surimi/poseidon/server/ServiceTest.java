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
import eu.project.surimi.poseidon.scenarios.ScenarioFilesForTesting;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import uk.ac.ox.poseidon.core.ScenarioSupplier;

import javax.measure.quantity.Mass;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static build.buf.gen.surimi.v1.RasterCellOrigin.RASTER_CELL_ORIGIN_CENTROID;
import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static java.lang.System.Logger.Level.INFO;
import static tech.units.indriya.unit.Units.KILOGRAM;

public abstract class ServiceTest {

    private final Class<? extends ScenarioSupplier> scenarioSupplierClass;

    public ServiceTest(final Class<? extends ScenarioSupplier> scenarioSupplierClass) {
        this.scenarioSupplierClass = scenarioSupplierClass;
    }

    static final javax.measure.Unit<Mass> MASS_UNIT = KILOGRAM;
    private static final String STEP_SIZE = "P1M";
    private static final LocalDateTime START_DATE_TIME =
        LocalDate.of(2000, 1, 1).atStartOfDay();
    private static final System.Logger logger =
        System.getLogger(WorkflowServiceTest.class.getName());
    private static final int PORT = 50051;
    io.grpc.Server server;
    ManagedChannel channel;
    protected WorkflowServiceGrpc.WorkflowServiceBlockingStub workflowStub;
    protected EcologyServiceGrpc.EcologyServiceBlockingStub ecologyStub;
    protected FisheryServiceGrpc.FisheryServiceBlockingStub fisheryStub;
    protected MarketServiceGrpc.MarketServiceBlockingStub marketStub;

    @BeforeEach
    void setUp() {
        try {
            final Path scenarioPath = ScenarioFilesForTesting.getPath(scenarioSupplierClass);
            server = new Server(scenarioPath, PORT).startServer();
            final ChannelCredentials credentials = InsecureChannelCredentials.create();
            channel = Grpc.newChannelBuilder("localhost:" + PORT, credentials).build();
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
        channel.shutdown();
        server.shutdown();
    }

    protected String initialiseSimulation() {
        return initialiseSimulation(UUID.randomUUID().toString()).getSimulationId();
    }

    protected SimulateStepResponse step(final String simulationId) {
        final SimulateStepRequest simulateStepRequest = SimulateStepRequest
            .newBuilder()
            .setSimulationId(simulationId)
            .build();
        return workflowStub.simulateStep(simulateStepRequest);
    }

    protected InitialiseResponse initialiseSimulation(final String simulationId) {
        return workflowStub.initialise(
            InitialiseRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .setScenarioId(scenarioSupplierClass.getSimpleName())
                .setSimulation(
                    Simulation
                        .newBuilder()
                        .setStartDateTime(toTimestamp(START_DATE_TIME))
                        .setTimeStep(STEP_SIZE)
                        .setGeography(
                            Geography
                                .newBuilder()
                                .setRasterCellOrigin(RASTER_CELL_ORIGIN_CENTROID)
                        )
                        .setStandards(
                            Standards
                                .newBuilder()
                                .setMeasurements(
                                    Measurement
                                        .newBuilder()
                                        .addUnits(
                                            Unit
                                                .newBuilder()
                                                .setQuantity("mass")
                                                .setUnit(KILOGRAM.getSymbol())
                                        )
                                )
                        )
                )
                .build()
        );
    }
}
