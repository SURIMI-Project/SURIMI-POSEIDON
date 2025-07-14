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

package eu.project.surimi.poseidon.server.market;

import build.buf.gen.surimi.v1.GetSpeciesPricesRequest;
import build.buf.gen.surimi.v1.GetSpeciesPricesResponse;
import build.buf.gen.surimi.v1.SpeciesPrice;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import uk.ac.ox.poseidon.agents.market.BiomassMarket;
import uk.ac.ox.poseidon.core.Simulation;

import java.util.Map;

import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static eu.project.surimi.poseidon.server.market.MarketService.getBiomassMarketsById;

public class GetSpeciesPricesRequestHandler extends
    WithSimulationRequestHandler<GetSpeciesPricesRequest, GetSpeciesPricesResponse> {

    private static final System.Logger logger =
        System.getLogger(GetSpeciesPricesRequestHandler.class.getName());

    public GetSpeciesPricesRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final GetSpeciesPricesRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected GetSpeciesPricesResponse getResponseWithSimulation(
        final GetSpeciesPricesRequest request,
        final Simulation simulation
    ) {
        logger.log(
            System.Logger.Level.INFO,
            "Prices requested for simulation {0}",
            request.getSimulationId()
        );
        final Map<String, BiomassMarket> marketsById = getBiomassMarketsById(simulation);
        final GetSpeciesPricesResponse.Builder responseBuilder =
            GetSpeciesPricesResponse
                .newBuilder()
                .setSimulationId(request.getSimulationId());
        marketsById.forEach((marketCode, biomassMarket) ->
            biomassMarket.getPrices().forEach((species, price) ->
                responseBuilder.addPrices(
                    SpeciesPrice.newBuilder()
                        .setMarketCode(marketCode)
                        .setSpecies(
                            build.buf.gen.surimi.v1.Species
                                .newBuilder()
                                .setSpeciesCode(species.getCode())
                        )
                        .setCurrency(price.getAmount().getCurrencyUnit().getCode())
                        .setPrice(price.getAmount().getAmount().doubleValue())
                        .setMeasurementUnit(price.getBiomassUnit().getSymbol())
                        .setGearCode("PS") // TODO
                        .setTimestamp(toTimestamp(simulation.getTemporalSchedule().getDateTime()))
                )
            )
        );
        return responseBuilder.build();
    }
}
