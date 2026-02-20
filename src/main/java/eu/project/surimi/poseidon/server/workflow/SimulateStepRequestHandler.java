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

package eu.project.surimi.poseidon.server.workflow;

import build.buf.gen.surimi.v1.SimulateStepRequest;
import build.buf.gen.surimi.v1.SimulateStepResponse;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import uk.ac.ox.poseidon.core.Simulation;

import java.time.Period;

import static java.lang.System.Logger.Level.INFO;

public class SimulateStepRequestHandler extends
    WithSimulationRequestHandler<SimulateStepRequest, SimulateStepResponse> {

    private static final System.Logger logger =
        System.getLogger(SimulateStepRequestHandler.class.getName());

    public SimulateStepRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final SimulateStepRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected SimulateStepResponse getResponseWithSimulation(
        final SimulateStepRequest request,
        final Simulation simulation,
        final SimulationManager.SimulationProperties simulationProperties
    ) {
        final Period stepSize = simulationManager.getSimulationProperties(simulation).getStepSize();
        log(INFO, simulation, "Step requested");
        simulation.getTemporalSchedule().stepFor(simulation, stepSize);
        log(INFO, simulation, "Stepped by {0}", stepSize);
        logMemoryUsage(simulation);
        return SimulateStepResponse
            .newBuilder()
            .setSimulationId(request.getSimulationId())
            .build();
    }
}
