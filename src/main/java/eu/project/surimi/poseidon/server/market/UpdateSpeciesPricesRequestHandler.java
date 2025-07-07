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

import build.buf.gen.surimi.v1.UpdateSpeciesPricesRequest;
import build.buf.gen.surimi.v1.UpdateSpeciesPricesResponse;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import org.joda.money.CurrencyUnit;
import org.joda.money.IllegalCurrencyException;
import org.joda.money.Money;
import uk.ac.ox.poseidon.agents.market.BiomassMarket;
import uk.ac.ox.poseidon.agents.market.Price;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.utils.Measurements;

import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import javax.measure.quantity.Mass;
import java.math.RoundingMode;
import java.util.Map;

import static eu.project.surimi.poseidon.server.market.MarketService.getBiomassMarketsById;
import static io.grpc.Status.INVALID_ARGUMENT;
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

    private static Map<String, Species> getSpeciesByCode(final Simulation simulation) {
        return simulation
            .getComponents(Species.class)
            .stream()
            .collect(toMap(Species::getCode, identity()));
    }

    @Override
    protected String getSimulationId(final UpdateSpeciesPricesRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected UpdateSpeciesPricesResponse getResponseWithSimulation(
        final UpdateSpeciesPricesRequest request,
        final Simulation simulation
    ) {
        logger.log(INFO, "Price update received for simulation {0}", request.getSimulationId());
        final Map<String, Species> speciesByCode = getSpeciesByCode(simulation);
        final Map<String, BiomassMarket> marketsById = getBiomassMarketsById(simulation);
        request.getPricesList().forEach(price -> {
            final BiomassMarket market = getOrThrow(
                marketsById,
                price.getMarketCode(),
                "Market"
            );
            final Species species = getOrThrow(
                speciesByCode,
                price.getSpeciesCode(),
                "Species"
            );
            final CurrencyUnit currencyUnit = parseCurrency(price.getCurrency());
            final Unit<Mass> biomassUnit = parseMassUnit(price.getMeasurementUnit());
            final Price marketPrice =
                new Price(
                    Money.of(currencyUnit, price.getPrice(), RoundingMode.HALF_EVEN),
                    biomassUnit
                );
            market.setPrice(species, marketPrice);
            logger.log(
                INFO,
                "Updated price of species {0} at port market {1} to {2}/{3}.",
                species.getCode(),
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

    private Unit<Mass> parseMassUnit(final String massUnit) {
        try {
            return Measurements.parseMassUnit(massUnit);
        } catch (final MeasurementParseException e) {
            throw wrap(INVALID_ARGUMENT, e);
        }
    }

}
