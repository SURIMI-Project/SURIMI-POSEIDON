package eu.project.surimi.poseidon.server;

import eu.project.surimi.Agents;
import uk.ac.ox.poseidon.agents.behaviours.fishing.FishingAction;
import uk.ac.ox.poseidon.core.Simulation;

import static eu.project.surimi.poseidon.server.FishingActionDataExtractor.extractBiomassGrids;
import static eu.project.surimi.poseidon.server.FishingActionDataExtractor.extractFishingActionData;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class GetGrossCatchesRequestHandler extends
    WithSimulationRequestHandler<Agents.GetGrossCatchesRequest, Agents.GetGrossCatchesResponse> {

    public GetGrossCatchesRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final Agents.GetGrossCatchesRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected Agents.GetGrossCatchesResponse getResponseWithSimulation(
        final Agents.GetGrossCatchesRequest request,
        final Simulation simulation
    ) {
        return Agents.GetGrossCatchesResponse
            .newBuilder()
            .setMeasurementUnit(KILOGRAM.getSymbol())
            .addAllBiomassGrids(
                extractBiomassGrids(
                    extractFishingActionData(
                        simulation,
                        request.getStartDateTime(),
                        request.getEndDateTime(),
                        FishingAction::getGrossCatch
                    )
                )
            )
            .build();
    }
}
