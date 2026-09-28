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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import uk.ac.ox.poseidon.core.Simulation;

/**
 * Base class for a gRPC method handler that operates on a live {@link Simulation}: looks it up
 * (via {@link #getSimulationId}) and its {@link SimulationManager.SimulationProperties}, then
 * delegates to {@link #getResponseWithSimulation} while holding the simulation's schedule lock,
 * so no two requests can step or mutate the same simulation concurrently.
 */
@RequiredArgsConstructor
public abstract class WithSimulationRequestHandler<ReqT, RespT>
    extends RequestHandler<ReqT, RespT> {

    protected final SimulationManager simulationManager;

    @SuppressWarnings("SynchronizeOnNonFinalField")
    @SuppressFBWarnings("USO")
    @Override
    protected RespT getResponse(final ReqT request) {
        final Simulation simulation = simulationManager.getSimulation(getSimulationId(request));
        final SimulationManager.SimulationProperties simulationProperties =
            simulationManager.getSimulationProperties(simulation);
        synchronized (simulation.schedule) {
            return getResponseWithSimulation(request, simulation, simulationProperties);
        }
    }

    /**
     * @return the simulation ID the request refers to.
     */
    protected abstract String getSimulationId(final ReqT request);

    /**
     * @return the response for the given request, with the simulation's schedule lock held.
     */
    protected abstract RespT getResponseWithSimulation(
        final ReqT request,
        final Simulation simulation,
        SimulationManager.SimulationProperties simulationProperties
    );

}
