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

package eu.project.surimi.poseidon.server.simulation;

import build.buf.gen.surimi.v1.InitialiseSimulationRequest;
import build.buf.gen.surimi.v1.InitialiseSimulationResponse;
import eu.project.surimi.poseidon.server.RequestHandler;
import eu.project.surimi.poseidon.server.SimulationManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.beanutils.PropertyUtils;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.SimulationStartOptions;
import uk.ac.ox.poseidon.core.utils.Measurements;
import uk.ac.ox.poseidon.io.ScenarioLoader;

import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import javax.measure.quantity.Mass;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import static build.buf.gen.surimi.v1.RasterCellOrigin.RASTER_CELL_ORIGIN_CENTROID;
import static eu.project.surimi.poseidon.server.Server.toLocalDateTime;
import static io.grpc.Status.*;
import static java.lang.System.Logger.Level.INFO;
import static uk.ac.ox.poseidon.core.time.Factories.dateTime;

@RequiredArgsConstructor
public class InitialiseRequestHandler
    extends RequestHandler<InitialiseSimulationRequest, InitialiseSimulationResponse> {

    private static final System.Logger logger =
        System.getLogger(InitialiseRequestHandler.class.getName());

    private final SimulationManager simulationManager;
    private final ScenarioLoader scenarioLoader;
    private final File scenarioFile;

    @Override
    protected InitialiseSimulationResponse getResponse(final InitialiseSimulationRequest request) {
        final UUID simulationId = SimulationManager.parseId(request.getSimulationId());
        if (simulationManager.contains(simulationId)) {
            throw ALREADY_EXISTS
                .withDescription("Simulation already initialised: " + simulationId)
                .asRuntimeException();
        }
        final Scenario scenario = scenarioLoader.load(scenarioFile);
        scenario.setStartingDateTime(
            dateTime(toLocalDateTime(request.getSimulation().getStartDateTime()))
        );

        logger.log(INFO, "Scenario loaded: {0}", scenarioFile.toPath().toAbsolutePath());

        final Period stepSize = parsePeriod(request.getSimulation().getTimeStep());
        final Unit<Mass> massUnit = getMassUnit(request);

        validateContract(request);

        final Simulation simulation = scenario.startNewSimulation(
            SimulationStartOptions.builder().simulationId(simulationId).build()
        );
        log(INFO, simulation, "Simulation started");
        logMemoryUsage(simulation);
        simulationManager.put(
            simulationId,
            simulation,
            new SimulationManager.SimulationProperties(stepSize, massUnit)
        );
        return InitialiseSimulationResponse
            .newBuilder()
            .setSimulationId(simulationId.toString())
            .build();
    }

    private void validateContract(final InitialiseSimulationRequest request) {
        final boolean isRasterCellOriginCentroid = request
            .getSimulation()
            .getGeography()
            .getRasterCellOrigin()
            .equals(RASTER_CELL_ORIGIN_CENTROID);
        if (!isRasterCellOriginCentroid) {
            throw FAILED_PRECONDITION
                .withDescription("Only centroid raster cell origin is supported.")
                .asRuntimeException();
        }
    }

    private static Unit<Mass> getMassUnit(final InitialiseSimulationRequest request) {
        return request.getSimulation()
            .getStandards()
            .getMeasurements()
            .getUnitsList()
            .stream()
            .filter(unit -> unit.getQuantity().equals("mass"))
            .findFirst()
            .map(unit -> parseMassUnit(unit.getUnit()))
            .orElseThrow(() ->
                NOT_FOUND
                    .withDescription("Standard unit for mass not found.")
                    .asRuntimeException()
            );
    }

    private static Unit<Mass> parseMassUnit(final String massUnit) {
        try {
            return Measurements.parseMassUnit(massUnit);
        } catch (final MeasurementParseException e) {
            throw wrap(INVALID_ARGUMENT, e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void setScenarioProperty(
        final Scenario scenario,
        final String propertyName,
        final Object value
    ) {
        try {
            PropertyUtils.setProperty(scenario, propertyName, value);
        } catch (
            final IllegalAccessException | InvocationTargetException | NoSuchMethodException e
        ) {
            throw FAILED_PRECONDITION
                .withDescription("Unable to set property " + propertyName + " to " + value)
                .withCause(e)
                .asRuntimeException();
        }
    }

    private Period parsePeriod(final String period) {
        try {
            return Period.parse(period);
        } catch (final DateTimeParseException e) {
            throw INVALID_ARGUMENT
                .withDescription("Invalid period: " + period)
                .withCause(e)
                .asRuntimeException();
        }
    }

}
