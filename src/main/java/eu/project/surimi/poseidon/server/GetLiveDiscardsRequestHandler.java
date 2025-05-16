package eu.project.surimi.poseidon.server;

import eu.project.surimi.Agents;
import uk.ac.ox.poseidon.core.Simulation;

import static eu.project.surimi.poseidon.server.FishingActionDataExtractor.extractBiomassGrids;
import static eu.project.surimi.poseidon.server.FishingActionDataExtractor.extractFishingActionData;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class GetLiveDiscardsRequestHandler extends
    WithSimulationRequestHandler<Agents.GetLiveDiscardsRequest, Agents.GetLiveDiscardsResponse> {

    public GetLiveDiscardsRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final Agents.GetLiveDiscardsRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected Agents.GetLiveDiscardsResponse getResponseWithSimulation(
        final Agents.GetLiveDiscardsRequest request,
        final Simulation simulation
    ) {
        return Agents.GetLiveDiscardsResponse
            .newBuilder()
            .setMeasurementUnit(KILOGRAM.getSymbol())
            .addAllBiomassGrids(
                extractBiomassGrids(
                    extractFishingActionData(
                        simulation,
                        request.getStartDateTime(),
                        request.getEndDateTime(),
                        action -> action.getDisposition().getDiscardedAlive()
                    )
                )
            )
            .build();
    }
}
