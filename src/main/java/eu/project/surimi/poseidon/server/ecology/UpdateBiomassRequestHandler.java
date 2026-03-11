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
import build.buf.gen.surimi.v1.UpdateBiomassRequest;
import build.buf.gen.surimi.v1.UpdateBiomassResponse;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.SpeciesKey;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import sim.util.Int2D;
import uk.ac.ox.poseidon.biology.biomass.BiomassGrid;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.geography.Coordinate;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

import java.util.Map;
import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkArgument;
import static eu.project.surimi.poseidon.server.Utils.checkRequestDateTimeAlignment;
import static io.grpc.Status.FAILED_PRECONDITION;
import static io.grpc.Status.NOT_FOUND;
import static java.lang.System.Logger.Level.INFO;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;

public class UpdateBiomassRequestHandler extends
    WithSimulationRequestHandler<UpdateBiomassRequest, UpdateBiomassResponse> {

    private static final System.Logger logger =
        System.getLogger(UpdateBiomassRequestHandler.class.getName());

    public UpdateBiomassRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    private static Int2D getSimulationCell(
        final build.buf.gen.surimi.v1.BiomassCell biomassCell,
        final BathymetricGrid bathymetricGrid
    ) {
        final Coordinate coordinate = new Coordinate(
            biomassCell.getLongitude(),
            biomassCell.getLatitude()
        );
        final ModelGrid modelGrid = bathymetricGrid.getModelGrid();
        modelGrid.checkIsInGrid(coordinate);
        final Int2D cell = modelGrid.toCell(coordinate);
        checkArgument(
            bathymetricGrid.isActiveWater(cell),
            "Coordinates %s do not point to an active water cell.",
            coordinate
        );
        return cell;
    }

    @Override
    protected String getSimulationId(final UpdateBiomassRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected UpdateBiomassResponse getResponseWithSimulation(
        final UpdateBiomassRequest request,
        final Simulation simulation,
        final SimulationManager.SimulationProperties simulationProperties
    ) {
        logger.log(INFO, "Biomass update received for simulation {0}", request.getSimulationId());
        checkRequestDateTimeAlignment(request.getDateTime(), simulation);

        final BiomassSummary biomassSummary = request.getBiomassSummary();
        final Map<SpeciesKey, BiomassGrid> simulationGrids =
            simulation.getComponents(BiomassGrid.class).stream().collect(toMap(
                grid -> SpeciesKey.from(grid.getSpecies()),
                identity()
            ));
        final BathymetricGrid bathymetricGrid = getBathymetricGrid(simulation);
        biomassSummary.getBiomassGridsList().forEach(biomassGrid -> {
            final BiomassGrid simulationGrid =
                simulationGrids.get(SpeciesKey.from(biomassGrid.getSpecies()));
            if (simulationGrid != null) {
                biomassGrid.getBiomassCellsList().forEach(biomassCell -> {
                    final Int2D cell = getSimulationCell(biomassCell, bathymetricGrid);
                    simulationGrid.setBiomass(
                        cell,
                        simulationProperties.convertMassInStandardUnitToKg(biomassCell.getBiomass())
                    );
                });
            }
        });
        return UpdateBiomassResponse
            .newBuilder()
            .setSimulationId(request.getSimulationId())
            .build();
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
}
