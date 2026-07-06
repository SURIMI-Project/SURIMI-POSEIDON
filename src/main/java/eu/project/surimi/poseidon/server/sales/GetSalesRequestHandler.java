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

package eu.project.surimi.poseidon.server.sales;

import build.buf.gen.surimi.v1.*;
import com.google.common.collect.Range;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.SpeciesKey;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import uk.ac.ox.poseidon.agents.catches.CatchCategory;
import uk.ac.ox.poseidon.agents.market.BiomassSaleAccumulator;
import uk.ac.ox.poseidon.agents.market.Market;
import uk.ac.ox.poseidon.biology.biomass.Biomass;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static eu.project.surimi.poseidon.server.Server.toLocalDateTime;
import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toProtoSpecies;
import static java.lang.System.Logger.Level.INFO;
import static java.util.stream.Collectors.*;

public class GetSalesRequestHandler extends
    WithSimulationRequestHandler<GetSalesRequest, GetSalesResponse> {

    private static final System.Logger logger =
        System.getLogger(GetSalesRequestHandler.class.getName());

    public GetSalesRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    private static Sale summariseSale(final List<SaleEntry> saleEntries) {
        final SaleEntry firstEntry = saleEntries.getFirst();
        final String catchCategoryCode = firstEntry.catchCategory.getCode();
        final double totalKg = saleEntries.stream()
            .mapToDouble(saleEntry -> saleEntry.biomass.asKg())
            .sum();
        final double totalValue = saleEntries.stream()
            .map(SaleEntry::value)
            .reduce(Money::plus)
            .map(money -> money.getAmount().doubleValue())
            .orElse(0.0);
        return Sale.newBuilder()
            .setSpecies(toProtoSpecies(firstEntry.species))
            .setFleetSegment(FleetSegment.newBuilder().setGearCode(firstEntry.gearCode).build())
            .setCategoryCode(catchCategoryCode)
            .setQuantity(totalKg)
            .setValue(totalValue)
            .build();
    }

    @Override
    protected String getSimulationId(final GetSalesRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected GetSalesResponse getResponseWithSimulation(
        final GetSalesRequest request,
        final Simulation simulation,
        final SimulationManager.SimulationProperties simulationProperties
    ) {
        log(INFO, simulation, "Sales requested");
        checkArgument(request.hasStartDateTime(), "Start date time is required.");
        checkArgument(request.hasEndDateTime(), "End date time is required.");
        final Range<LocalDateTime> dateTimeRange = Range.closed(
            toLocalDateTime(request.getStartDateTime()),
            toLocalDateTime(request.getEndDateTime())
        );
        record Key(Market market, CatchCategory catchCategory, CurrencyUnit currencyUnit) {}
        final List<MarketSales> marketSales =
            simulation
                .getComponent(BiomassSaleAccumulator.class)
                .getEvents()
                .filter(sale ->
                    dateTimeRange.contains(sale.getDateTime()) &&
                        simulationProperties.getMarketCodes().contains(sale.getMarket().getCode())
                )
                .flatMap(sale ->
                    sale
                        .getItems()
                        .stream()
                        .filter(item ->
                            // only report sales for species in contract
                            simulationProperties
                                .getSpeciesKeys()
                                .contains(SpeciesKey.from(item.getSpecies()))
                        )
                        .map(item -> new SaleEntry(
                            sale.getMarket(),
                            sale.getVessel().getGear().getCode(),
                            item.getCategory(),
                            item.getSpecies(),
                            item.getContent().asBiomass(),
                            item.getSaleValue()
                        ))
                ).collect(
                    groupingBy(
                        saleEntry -> new Key(
                            saleEntry.market,
                            saleEntry.catchCategory,
                            saleEntry.value.getCurrencyUnit()
                        ),
                        collectingAndThen(
                            groupingBy(
                                saleEntry -> saleEntry.species,
                                collectingAndThen(
                                    toList(),
                                    GetSalesRequestHandler::summariseSale
                                )
                            ),
                            Map::values
                        )
                    )
                )
                .entrySet()
                .stream()
                .map(entry ->
                    MarketSales
                        .newBuilder()
                        .setMarketCode(entry.getKey().market().getCode())
                        .setCurrency(entry.getKey().currencyUnit().getCode())
                        .addAllSales(entry.getValue())
                        .build()
                )
                .toList();

        return GetSalesResponse
            .newBuilder()
            .setSimulationId(request.getSimulationId())
            .setStartDateTime(request.getStartDateTime())
            .setEndDateTime(request.getEndDateTime())
            .setSalesSummary(SalesSummary.newBuilder().addAllMarketSales(marketSales))
            .build();
    }

    private record SaleEntry(
        Market market,
        String gearCode,
        CatchCategory catchCategory,
        Species species,
        Biomass biomass,
        Money value
    ) {}

}
