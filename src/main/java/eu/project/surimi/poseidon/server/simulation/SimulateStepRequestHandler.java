/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025-2026, University of Oxford.
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

import static eu.project.surimi.poseidon.server.Server.toLocalDateTime;
import static eu.project.surimi.poseidon.server.Utils.checkRequestDateTimeAlignment;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.INFO;

import build.buf.gen.surimi.v1.SimulateStepRequest;
import build.buf.gen.surimi.v1.SimulateStepResponse;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import java.time.LocalDateTime;
import java.time.Period;
import uk.ac.ox.poseidon.core.Simulation;

/**
 * Handles {@code SimulateStep}: checks the request's date-time is the
 * simulation's current one, then advances the simulation's schedule to the
 * request's date-time plus its configured step size. The events dated exactly
 * then run in the next step.
 */
public class SimulateStepRequestHandler
  extends WithSimulationRequestHandler<
    SimulateStepRequest,
    SimulateStepResponse
  >
{

  private static final System.Logger logger = System.getLogger(
    SimulateStepRequestHandler.class.getName()
  );

  /** @param simulationManager the simulations this handler can reach */
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
    checkRequestDateTimeAlignment(request.getCurrentDateTime(), simulation);
    final Period stepSize = simulationManager
      .getSimulationProperties(simulation)
      .getStepSize();
    log(INFO, simulation, "Step requested");
    // Step to a date-time computed from the controller's, so that steps stay on
    // the controller's calendar. stepUntil leaves the events dated at its
    // target to the next step: events at the next step's start (such as the
    // monthly clearing of the accumulators the controller reads after this
    // step) belong to it.
    final LocalDateTime nextStepStart = toLocalDateTime(
      request.getCurrentDateTime()
    ).plus(stepSize);
    simulation.getTemporalSchedule().stepUntil(simulation, nextStepStart);
    log(DEBUG, simulation, "Stepped to {0}", nextStepStart);
    logMemoryUsage(simulation);
    return SimulateStepResponse.newBuilder()
      .setSimulationId(request.getSimulationId())
      .build();
  }
}
