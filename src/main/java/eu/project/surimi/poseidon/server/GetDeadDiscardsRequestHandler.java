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

import eu.project.surimi.Agents;
import uk.ac.ox.poseidon.core.Simulation;

import static eu.project.surimi.poseidon.server.FishingActionDataExtractor.extractFleetBiomassGrids;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class GetDeadDiscardsRequestHandler extends
    WithSimulationRequestHandler<Agents.GetDeadDiscardsRequest, Agents.GetDeadDiscardsResponse> {

    public GetDeadDiscardsRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final Agents.GetDeadDiscardsRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected Agents.GetDeadDiscardsResponse getResponseWithSimulation(
        final Agents.GetDeadDiscardsRequest request,
        final Simulation simulation
    ) {
        return Agents.GetDeadDiscardsResponse
            .newBuilder()
            .setMeasurementUnit(KILOGRAM.getSymbol())
            .addAllFleetBiomassGrids(
                extractFleetBiomassGrids(
                    simulation,
                    request.getStartDateTime(),
                    request.getEndDateTime(),
                    action -> action.getDisposition().getDiscardedDead()
                )
            )
            .build();
    }
}
