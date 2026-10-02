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
import eu.project.surimi.poseidon.regulations.TotalAllowableCatchQuotas.QuotaDefinition;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.core.Simulation;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static eu.project.surimi.poseidon.server.Server.toInstant;
import static eu.project.surimi.poseidon.server.mappers.FleetSegmentProtoMapper.toPoseidonFleetSegment;
import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toPoseidonSpecies;

/**
 * Handles {@code UpdateRegulations}: registers the TAC entries in the request as new quotas on
 * the simulation's {@link TotalAllowableCatchQuotas} for the given interval, fleet segment, and
 * species. Entries whose fleet segment overlaps no contract fleet segment are ignored: they apply
 * to fleets simulated by other models (the fisheries authority sends every model the TACs of all
 * fleets). The remaining entries are registered all or nothing: if one is rejected, none is set.
 */
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
        checkArgument(
            request.hasStartDateTime(),
            "Update regulations request is missing a start date time."
        );
        checkArgument(
            request.hasEndDateTime(),
            "Update regulations request is missing an end date time."
        );
        checkArgument(
            request.hasRegulationsSummary(),
            "Update regulations request is missing a regulations summary."
        );
        final var startInstant = toInstant(request.getStartDateTime());
        final var endInstant = toInstant(request.getEndDateTime());
        checkArgument(
            !endInstant.isBefore(startInstant),
            "End date time must be on or after start date time."
        );
        final Interval interval = Interval.of(startInstant, endInstant);
        final List<QuotaDefinition> quotaDefinitions =
            request
                .getRegulationsSummary()
                .getTotalAllowableCatchesList()
                .stream()
                .map(totalAllowableCatch -> {
                    checkArgument(
                        totalAllowableCatch.hasSpecies(),
                        "TAC entry is missing a species."
                    );
                    checkArgument(
                        totalAllowableCatch.hasFleetSegment(),
                        "TAC entry is missing a fleet segment."
                    );
                    return new QuotaDefinition(
                        toPoseidonFleetSegment(totalAllowableCatch.getFleetSegment()),
                        toPoseidonSpecies(totalAllowableCatch.getSpecies()),
                        simulationProperties.convertMassInStandardUnitToKg(
                            totalAllowableCatch.getCatch()
                        )
                    );
                })
                .filter(quotaDefinition ->
                    simulationProperties.overlapsContractFleetSegment(quotaDefinition.fleetSegment())
                )
                .toList();
        simulation
            .getComponent(TotalAllowableCatchQuotas.class)
            .setQuotas(interval, quotaDefinitions);
        return UpdateRegulationsResponse
            .newBuilder()
            .setSimulationId(request.getSimulationId())
            .build();
    }
}
