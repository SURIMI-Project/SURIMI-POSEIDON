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
import build.buf.gen.surimi.v1.Species;
import build.buf.gen.surimi.v1.SpeciesPrice;
import build.buf.gen.surimi.v1.UpdateSpeciesPricesRequest;
import eu.project.surimi.poseidon.scenarios.MinimalScenario;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

import static eu.project.surimi.poseidon.scenarios.MinimalScenario.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SetPricesTest extends ServiceTest {
    public SetPricesTest() {
        super(MinimalScenario.class);
    }

    private String simulationId;

    @BeforeProperty
    void setUp() {
        super.setUp();
        simulationId = initialiseSimulation();
        step(simulationId);
    }

    @AfterProperty
    void tearDown() {
        super.tearDown();
    }

    @Property
    void myProperty(
        @ForAll @DoubleRange(max = 1E6) final double price,
        @ForAll("marketCodes") final String marketCode,
        @ForAll("currencyCodes") final String currencyCode,
        @ForAll("gearCodes") final String gearCode,
        @ForAll("speciesCodes") final String speciesCode
    ) {
        // noinspection ResultOfMethodCallIgnored
        marketStub.updateSpeciesPrices(
            UpdateSpeciesPricesRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .addPrices(
                    SpeciesPrice
                        .newBuilder()
                        .setMarketCode(marketCode)
                        .setCurrency(currencyCode)
                        .setGearCode(gearCode)
                        .setSpecies(Species.newBuilder().setSpeciesCode(speciesCode).build())
                        .setPrice(price)
                        .setMeasurementUnit("kg")
                        .build()
                )
                .build()
        );
        assertTrue(
            marketStub
                .getSpeciesPrices(
                    GetSpeciesPricesRequest.newBuilder().setSimulationId(simulationId).build()
                )
                .getPricesList()
                .stream()
                .anyMatch(speciesPrice ->
                    speciesPrice.getMarketCode().equals(marketCode) &&
                        speciesPrice.getCurrency().equals(currencyCode) &&
                        speciesPrice.getGearCode().equals(gearCode) &&
                        speciesPrice.getSpecies().getSpeciesCode().equals(speciesCode) &&
                        speciesPrice.getPrice() == price
                )
        );
    }

    @Provide
    Arbitrary<String> gearCodes() {return Arbitraries.of(GEAR_CODES);}

    @Provide
    Arbitrary<String> speciesCodes() {return Arbitraries.of(SPECIES_CODES);}

    @Provide
    Arbitrary<String> currencyCodes() {return Arbitraries.of("GBP", "EUR", "USD");}

    @Provide
    Arbitrary<String> marketCodes() {return Arbitraries.of(MARKET_CODES);}

}
