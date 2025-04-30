package eu.project.surimi.poseidon.server;

import com.google.common.collect.Range;
import eu.project.surimi.Agents;
import eu.project.surimi.Sales;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import uk.ac.ox.poseidon.agents.market.BiomassSaleAccumulator;
import uk.ac.ox.poseidon.agents.market.Market;
import uk.ac.ox.poseidon.biology.biomass.Biomass;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.*;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class GetSalesSummaryRequestHandler extends
    WithSimulationRequestHandler<Agents.GetSalesSummaryRequest, Agents.GetSalesSummaryResponse> {

    public GetSalesSummaryRequestHandler(SimulationManager simulationManager) {
        super(simulationManager);
    }

    private static Sales.Sale summariseSale(List<SaleEntry> saleEntries) {
        String speciesCode = saleEntries.getFirst().species.getCode();
        double totalKg = saleEntries.stream()
            .mapToDouble(saleEntry -> saleEntry.biomass.asKg())
            .sum();
        double totalValue = saleEntries.stream()
            .map(SaleEntry::value)
            .reduce(Money::plus)
            .map(money -> money.getAmount().doubleValue())
            .orElse(0.0);
        return Sales.Sale.newBuilder()
            .setSpeciesId(speciesCode)
            .setQuantity(totalKg)
            .setValue(totalValue)
            .build();
    }

    @Override
    protected String getSimulationId(Agents.GetSalesSummaryRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected Agents.GetSalesSummaryResponse getResponseWithSimulation(
        Agents.GetSalesSummaryRequest request,
        Simulation simulation
    ) {
        Range<LocalDateTime> dateTimeRange = Range.closed(
            toLocalDateTime(request.getStartDateTime()),
            toLocalDateTime(request.getEndDateTime())
        );
        System.out.println(dateTimeRange);
        System.out.println(simulation
            .getComponent(BiomassSaleAccumulator.class));
        System.out.println(simulation
            .getComponent(BiomassSaleAccumulator.class)
            .getEvents().toList());
        record Key(Market<?> market, CurrencyUnit currencyUnit) {}
        List<Sales.SalesSummary> saleSummaries =
            simulation
                .getComponent(BiomassSaleAccumulator.class)
                .getEvents()
                .filter(sale -> dateTimeRange.contains(sale.getDateTime()))
                .flatMap(sale ->
                    sale
                        .getSold()
                        .cellSet()
                        .stream()
                        .map(cell -> new SaleEntry(
                            sale.getMarket(),
                            cell.getRowKey(),
                            cell.getColumnKey(),
                            cell.getValue()
                        ))
                ).collect(
                    groupingBy(
                        saleEntry -> new Key(
                            saleEntry.market,
                            saleEntry.value.getCurrencyUnit()
                        ),
                        collectingAndThen(
                            groupingBy(
                                saleEntry -> saleEntry.species,
                                collectingAndThen(
                                    toList(),
                                    GetSalesSummaryRequestHandler::summariseSale
                                )
                            ),
                            Map::values
                        )
                    )
                )
                .entrySet()
                .stream()
                .map(entry ->
                    Sales.SalesSummary
                        .newBuilder()
                        .setMarketId(entry.getKey().market().getId())
                        .setMeasurementUnit(KILOGRAM.getSymbol())
                        .setCurrency(entry.getKey().currencyUnit().getCode())
                        .addAllSales(entry.getValue())
                        .setStartDateTime(request.getStartDateTime())
                        .setEndDateTime(request.getEndDateTime())
                        .build()
                )
                .toList();

        return Agents.GetSalesSummaryResponse
            .newBuilder()
            .addAllSalesSummaries(saleSummaries)
            .build();
    }

    private record SaleEntry(Market<?> market, Species species, Biomass biomass, Money value) {}

}
