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
import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import uk.ac.ox.poseidon.core.Scenario;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static build.buf.gen.surimi.v1.RasterCellOrigin.RASTER_CELL_ORIGIN_CENTROID;
import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static java.lang.System.Logger.Level.INFO;
import static tech.units.indriya.unit.Units.KILOGRAM;

public abstract class ServiceTest {

    private final Class<? extends Supplier<Scenario>> scenarioSupplierClass;

    public ServiceTest(final Class<? extends Supplier<Scenario>> scenarioSupplierClass) {
        this.scenarioSupplierClass = scenarioSupplierClass;
    }

    protected static final String STEP_SIZE = "P1M";
    protected static final LocalDateTime START_DATE_TIME =
        LocalDate.of(2000, 1, 1).atStartOfDay();
    private static final System.Logger logger =
        System.getLogger(SimulationServiceTest.class.getName());
    private static final int PORT = 0;
    protected SimulationManager simulationManager;
    io.grpc.Server server;
    ManagedChannel channel;
    protected FisheryServiceGrpc.FisheryServiceBlockingStub simulationStub;
    protected FisheryServiceGrpc.FisheryServiceBlockingStub catchProviderStub;
    protected FisheryServiceGrpc.FisheryServiceBlockingStub ecologyConsumerStub;
    protected FisheryServiceGrpc.FisheryServiceBlockingStub salesProviderStub;
    protected FisheryServiceGrpc.FisheryServiceBlockingStub speciesPriceConsumerStub;

    @BeforeEach
    protected void setUp() {
        try {
            final Path scenarioPath = ScenarioFilesForTesting.getPath(scenarioSupplierClass);
            simulationManager = new SimulationManager();
            server = new Server(scenarioPath.getParent(), PORT).startServer(simulationManager);
            final int boundPort = server.getPort();
            final ChannelCredentials credentials = InsecureChannelCredentials.create();
            channel = Grpc.newChannelBuilder("localhost:" + boundPort, credentials).build();
            simulationStub = FisheryServiceGrpc.newBlockingStub(channel);
            catchProviderStub = FisheryServiceGrpc.newBlockingStub(channel);
            ecologyConsumerStub = FisheryServiceGrpc.newBlockingStub(channel);
            salesProviderStub = FisheryServiceGrpc.newBlockingStub(channel);
            speciesPriceConsumerStub = FisheryServiceGrpc.newBlockingStub(channel);
        } catch (final InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    protected void tearDown() {
        logger.log(INFO, "Shutting down server");
        if (channel != null) {
            channel.shutdownNow();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (server != null) {
            server.shutdownNow();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected String initialiseSimulation() {
        return initialiseSimulation(UUID.randomUUID().toString()).getSimulationId();
    }

    protected SimulateStepResponse step(
        final String simulationId,
        final LocalDateTime currentDateTime
    ) {
        final SimulateStepRequest simulateStepRequest = SimulateStepRequest
            .newBuilder()
            .setSimulationId(simulationId)
            .setCurrentDateTime(toTimestamp(currentDateTime))
            .build();
        return simulationStub.simulateStep(simulateStepRequest);
    }

    protected InitialiseSimulationResponse initialiseSimulation(final String simulationId) {
        return initialiseSimulation(simulationId, scenarioSupplierClass.getSimpleName());
    }

    protected InitialiseSimulationResponse initialiseSimulation(
        final String simulationId,
        final String scenarioName
    ) {
        return simulationStub.initialiseSimulation(
            InitialiseSimulationRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .setScenarioName(scenarioName)
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
                        .setItems(
                            Items
                                .newBuilder()
                                .addAllMarkets(
                                    MinimalScenario.MARKET_CODES
                                        .stream()
                                        .map(marketCode ->
                                            Market
                                                .newBuilder()
                                                .setMarketCode(marketCode)
                                                .build()
                                        )
                                        .toList()
                                )
                                .addAllPriceCategories(
                                    MinimalScenario.GEAR_CODES
                                        .stream()
                                        .map(gearCode ->
                                            PriceCategory
                                                .newBuilder()
                                                .setCategoryCode(gearCode)
                                                .build()
                                        )
                                        .toList()
                                )
                                .addAllSpecies(
                                    MinimalScenario.LIFE_STAGE_PER_SPECIES_CODE
                                        .stream()
                                        .map(pair -> {
                                            final Species.Builder builder =
                                                Species.newBuilder();
                                            builder.setSpeciesCode(pair.getFirst());
                                            if (pair.getSecond() != null) {
                                                builder.setLifeStage(pair.getSecond());
                                            }
                                            return builder.build();
                                        })
                                        .toList()
                                )
                        )
                )
                .build()
        );
    }
}
