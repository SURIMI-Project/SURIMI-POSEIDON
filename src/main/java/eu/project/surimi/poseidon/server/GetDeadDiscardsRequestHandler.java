package eu.project.surimi.poseidon.server;

import eu.project.surimi.Agents;
import uk.ac.ox.poseidon.core.Simulation;

import static eu.project.surimi.poseidon.server.FishingActionDataExtractor.extractBiomassGrids;
import static eu.project.surimi.poseidon.server.FishingActionDataExtractor.extractFishingActionData;
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
            .addAllBiomassGrids(
                extractBiomassGrids(
                    extractFishingActionData(
                        simulation,
                        request.getStartDateTime(),
                        request.getEndDateTime(),
                        action -> action.getDisposition().getDiscardedDead()
                    )
                )
            )
            .build();
    }
}
