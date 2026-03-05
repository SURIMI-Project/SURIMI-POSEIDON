/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2026, University of Oxford.
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

package eu.project.surimi.poseidon.server.regulations;

import build.buf.gen.surimi.v1.UpdateRegulationsRequest;
import build.buf.gen.surimi.v1.UpdateRegulationsResponse;
import eu.project.surimi.poseidon.regulations.TotalAllowableCatchQuotas;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.core.Simulation;

import static com.google.common.base.Preconditions.checkArgument;
import static eu.project.surimi.poseidon.server.Server.toInstant;
import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toPoseidonSpecies;

public class UpdateRegulationsRequestHandler extends
    WithSimulationRequestHandler<UpdateRegulationsRequest, UpdateRegulationsResponse> {

    public UpdateRegulationsRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final UpdateRegulationsRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected UpdateRegulationsResponse getResponseWithSimulation(
        final UpdateRegulationsRequest request,
        final Simulation simulation,
        final SimulationManager.SimulationProperties simulationProperties
    ) {
        checkArgument(request.hasStartDateTime(), "Start date time is required.");
        checkArgument(request.hasEndDateTime(), "End date time is required.");
        checkArgument(request.hasRegulationsSummary(), "Regulations summary is required.");
        final var startInstant = toInstant(request.getStartDateTime());
        final var endInstant = toInstant(request.getEndDateTime());
        checkArgument(
            !endInstant.isBefore(startInstant),
            "End date time must be on or after start date time."
        );
        final Interval interval = Interval.of(startInstant, endInstant);
        final TotalAllowableCatchQuotas totalAllowableCatchQuotas =
            simulation.getComponent(TotalAllowableCatchQuotas.class);
        request
            .getRegulationsSummary()
            .getTotalAllowableCatchesList()
            .forEach(totalAllowableCatch -> {
                checkArgument(
                    totalAllowableCatch.hasSpecies(),
                    "Each TAC entry must include a species."
                );
                final double quotaInKg =
                    simulationProperties
                        .convertMassInStandardUnitToKg(
                            totalAllowableCatch.getCatch()
                        );
                totalAllowableCatchQuotas.setQuota(
                    interval,
                    toPoseidonSpecies(totalAllowableCatch.getSpecies()),
                    quotaInKg
                );
            });
        return UpdateRegulationsResponse
            .newBuilder()
            .setSimulationId(request.getSimulationId())
            .build();
    }
}
