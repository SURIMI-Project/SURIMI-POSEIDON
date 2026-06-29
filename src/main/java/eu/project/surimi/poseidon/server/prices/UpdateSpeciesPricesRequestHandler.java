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

package eu.project.surimi.poseidon.server.prices;

import build.buf.gen.surimi.v1.UpdateSpeciesPricesRequest;
import build.buf.gen.surimi.v1.UpdateSpeciesPricesResponse;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import org.joda.money.CurrencyUnit;
import org.joda.money.IllegalCurrencyException;
import org.joda.money.Money;
import uk.ac.ox.poseidon.agents.catches.CatchCategory;
import uk.ac.ox.poseidon.agents.market.BiomassMarket;
import uk.ac.ox.poseidon.agents.market.MarketGrid;
import uk.ac.ox.poseidon.agents.market.Price;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.geography.grids.ObjectGrid;

import javax.measure.Unit;
import javax.measure.quantity.Mass;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toPoseidonSpecies;
import static io.grpc.Status.FAILED_PRECONDITION;
import static io.grpc.Status.INVALID_ARGUMENT;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.INFO;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;

public class UpdateSpeciesPricesRequestHandler extends
    WithSimulationRequestHandler<UpdateSpeciesPricesRequest, UpdateSpeciesPricesResponse> {

    private static final System.Logger logger =
        System.getLogger(UpdateSpeciesPricesRequestHandler.class.getName());

    public UpdateSpeciesPricesRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    @Override
    protected String getSimulationId(final UpdateSpeciesPricesRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected UpdateSpeciesPricesResponse getResponseWithSimulation(
        final UpdateSpeciesPricesRequest request,
        final Simulation simulation,
        final SimulationManager.SimulationProperties simulationProperties
    ) {
        logger.log(INFO, "Price update received for simulation {0}", request.getSimulationId());

        final Map<String, BiomassMarket> marketsById = getMarketsById(simulation);
        request.getSpeciesPriceSummary().getSpeciesPricesList().forEach(price -> {
            final BiomassMarket market = getOrThrow(
                marketsById,
                price.getMarketCode(),
                "Market"
            );

            final CurrencyUnit currencyUnit = parseCurrency(price.getCurrency());
            final Unit<Mass> biomassUnit = simulationProperties.getStandardMassUnit();
            final Price marketPrice =
                new Price(
                    Money.of(currencyUnit, price.getPrice(), RoundingMode.HALF_EVEN),
                    biomassUnit
                );
            final CatchCategory catchCategory = new CatchCategory(price.getCategoryCode());
            final Species requestSpecies = toPoseidonSpecies(price.getSpecies());
            validateNoGenericStagedConflict(market, catchCategory, requestSpecies);

            market.setPrice(catchCategory, requestSpecies, marketPrice);
            logger.log(
                DEBUG,
                "Updated price of species {0} at port market {1} to {2}/{3}.",
                requestSpecies,
                market.getCode(),
                marketPrice.getAmount(),
                marketPrice.getBiomassUnit()
            );

        });
        return UpdateSpeciesPricesResponse
            .newBuilder()
            .setSimulationId(request.getSimulationId())
            .build();
    }

    private CurrencyUnit parseCurrency(final String currency) {
        try {
            return CurrencyUnit.of(currency);
        } catch (final IllegalCurrencyException e) {
            throw wrap(INVALID_ARGUMENT, e);
        }
    }

    private void validateNoGenericStagedConflict(
        final BiomassMarket market,
        final CatchCategory catchCategory,
        final Species requestedSpecies
    ) {
        final Map<Species, Price> pricesBySpecies = market.getPrices().get(catchCategory);
        if (pricesBySpecies == null || pricesBySpecies.isEmpty()) {
            return;
        }
        final String speciesCode = requestedSpecies.getCode();
        final boolean requestIsGeneric = requestedSpecies.getLifeStage() == null;
        final boolean hasGenericPrice = pricesBySpecies
            .keySet()
            .stream()
            .anyMatch(species ->
                species.getCode().equals(speciesCode) && species.getLifeStage() == null
            );
        final boolean hasStagedPrice = pricesBySpecies
            .keySet()
            .stream()
            .anyMatch(species ->
                species.getCode().equals(speciesCode) && species.getLifeStage() != null
            );
        if (requestIsGeneric && hasStagedPrice) {
            throw INVALID_ARGUMENT
                .withDescription(
                    ("Cannot update generic species '%s' in market '%s' and gear '%s' because " +
                        "staged prices exist.")
                        .formatted(speciesCode, market.getCode(), catchCategory.getCode())
                )
                .asRuntimeException();
        }
        if (!requestIsGeneric && hasGenericPrice) {
            throw INVALID_ARGUMENT
                .withDescription(
                    ("Cannot update staged species '%s' in market '%s' and gear '%s' because a " +
                        "generic price exists.")
                        .formatted(requestedSpecies, market.getCode(), catchCategory.getCode())
                )
                .asRuntimeException();
        }
    }

    static Set<MarketGrid> getMarketGrids(final Simulation simulation) {
        final Set<MarketGrid> marketGrids =
            simulation.getComponents(MarketGrid.class);
        if (marketGrids.isEmpty()) {
            throw FAILED_PRECONDITION
                .withDescription("No market grids defined in simulation.")
                .asRuntimeException();
        }
        return marketGrids;
    }

    static Map<String, BiomassMarket> getMarketsById(final Simulation simulation) {
        return getMarketGrids(simulation)
            .stream()
            .flatMap(ObjectGrid::stream)
            .filter(BiomassMarket.class::isInstance)
            .map(BiomassMarket.class::cast)
            .collect(toMap(BiomassMarket::getCode, identity()));
    }

}
