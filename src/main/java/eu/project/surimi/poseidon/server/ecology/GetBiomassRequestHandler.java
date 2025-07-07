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

package eu.project.surimi.poseidon.server.ecology;

import build.buf.gen.surimi.v1.BiomassSummary;
import build.buf.gen.surimi.v1.GetBiomassRequest;
import build.buf.gen.surimi.v1.GetBiomassResponse;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import uk.ac.ox.poseidon.biology.biomass.BiomassGrid;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.geography.Coordinate;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;

import java.util.NoSuchElementException;

import static io.grpc.Status.FAILED_PRECONDITION;
import static io.grpc.Status.NOT_FOUND;
import static java.lang.System.Logger.Level.INFO;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class GetBiomassRequestHandler extends
    WithSimulationRequestHandler<GetBiomassRequest, GetBiomassResponse> {

    private static final System.Logger logger =
        System.getLogger(GetBiomassRequestHandler.class.getName());

    public GetBiomassRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final GetBiomassRequest request) {
        return request.getSimulationId();
    }

    private BathymetricGrid getBathymetricGrid(final Simulation simulation) {
        try {
            return simulation.getComponent(BathymetricGrid.class);
        } catch (final NoSuchElementException e) {
            throw wrap(NOT_FOUND, e);
        } catch (final IllegalStateException e) {
            throw wrap(FAILED_PRECONDITION, e);
        }
    }

    @Override
    protected GetBiomassResponse getResponseWithSimulation(
        final GetBiomassRequest request,
        final Simulation simulation
    ) {
        logger.log(INFO, "Biomass requested for simulation {0}", request.getSimulationId());
        final BathymetricGrid bathymetricGrid = getBathymetricGrid(simulation);
        final BiomassSummary.Builder biomassSummaryBuilder =
            BiomassSummary
                .newBuilder()
                .setMeasurementUnit(KILOGRAM.getSymbol());
        simulation.getComponents(BiomassGrid.class).forEach(grid -> {
            final build.buf.gen.surimi.v1.BiomassGrid.Builder gridBuilder =
                build.buf.gen.surimi.v1.BiomassGrid
                    .newBuilder()
                    .setSpeciesCode(grid.getSpecies().getCode());
            bathymetricGrid.getActiveWaterCells().forEach(cell -> {
                final Coordinate coordinate =
                    bathymetricGrid.getModelGrid().toCoordinate(cell);
                gridBuilder.addBiomassCells(
                    build.buf.gen.surimi.v1.BiomassCell
                        .newBuilder()
                        .setLongitude(coordinate.getLon())
                        .setLatitude(coordinate.getLat())
                        .setBiomass(grid.getDouble(cell))
                        .build()
                );
            });
            biomassSummaryBuilder.addBiomassGrids(gridBuilder);
        });
        return GetBiomassResponse
            .newBuilder()
            .setSimulationId(request.getSimulationId())
            .setBiomassSummary(biomassSummaryBuilder)
            .build();
    }
}
