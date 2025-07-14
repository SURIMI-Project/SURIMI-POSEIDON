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

package eu.project.surimi.poseidon.server;

import build.buf.gen.surimi.v1.GetSpeciesPricesRequest;
import build.buf.gen.surimi.v1.GetSpeciesPricesResponse;
import build.buf.gen.surimi.v1.SpeciesPrice;
import eu.project.surimi.poseidon.scenarios.MinimalScenario;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.agents.market.Price;

import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.units.indriya.unit.Units.KILOGRAM;
import static uk.ac.ox.poseidon.core.utils.Measurements.parseMassUnit;

public class MarketServiceTest extends ServiceTest {

    public MarketServiceTest() {
        super(MinimalScenario.class);
    }

    @Test
    void getPrices() {
        final String simulationId = initialiseSimulation();
        final GetSpeciesPricesResponse response =
            marketStub.getSpeciesPrices(
                GetSpeciesPricesRequest
                    .newBuilder()
                    .setSimulationId(simulationId)
                    .build()
            );
        final Map<String, Map<String, Price>> prices = readPricesResponse(response);
        final CurrencyUnit gbp = CurrencyUnit.of("GBP");
        assertEquals(
            Set.of(KILOGRAM),
            prices
                .values()
                .stream()
                .flatMap(m -> m.values().stream())
                .map(Price::getBiomassUnit)
                .collect(toSet())
        );
        assertEquals(Money.of(gbp, 1.00), prices.get("P1").get("A").getAmount());
        assertEquals(Money.of(gbp, 1.10), prices.get("P1").get("B").getAmount());
        assertEquals(Money.of(gbp, 1.20), prices.get("P1").get("C").getAmount());
        assertEquals(Money.of(gbp, 2.00), prices.get("P2").get("A").getAmount());
        assertEquals(Money.of(gbp, 2.10), prices.get("P2").get("B").getAmount());
        assertEquals(Money.of(gbp, 2.20), prices.get("P2").get("C").getAmount());
    }

    Map<String, Map<String, Price>> readPricesResponse(final GetSpeciesPricesResponse response) {
        return response.getPricesList().stream()
            .collect(groupingBy(
                SpeciesPrice::getMarketCode,
                toMap(
                    sp -> sp.getSpecies().getSpeciesCode(),
                    sp -> new Price(
                        Money.of(CurrencyUnit.of(sp.getCurrency()), sp.getPrice()),
                        parseMassUnit(sp.getMeasurementUnit())
                    )
                )
            ));
    }
}
