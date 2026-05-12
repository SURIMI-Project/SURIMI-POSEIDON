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

import build.buf.gen.surimi.v1.FinaliseSimulationRequest;
import build.buf.gen.surimi.v1.FinaliseSimulationResponse;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import uk.ac.ox.poseidon.core.Simulation;

import static java.lang.System.Logger.Level.INFO;

public class FinaliseRequestHandler
    extends WithSimulationRequestHandler<FinaliseSimulationRequest, FinaliseSimulationResponse> {

    private static final System.Logger logger =
        System.getLogger(FinaliseRequestHandler.class.getName());

    public FinaliseRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final FinaliseSimulationRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected FinaliseSimulationResponse getResponseWithSimulation(
        final FinaliseSimulationRequest request,
        final Simulation simulation,
        final SimulationManager.SimulationProperties simulationProperties
    ) {
        logger.log(INFO, "Finalising simulation {0}", request.getSimulationId());
        simulation.finish();
        final String simulationId = request.getSimulationId();
        simulationManager.remove(simulationId);
        return FinaliseSimulationResponse
            .newBuilder()
            .setSimulationId(simulationId)
            .build();
    }
}
