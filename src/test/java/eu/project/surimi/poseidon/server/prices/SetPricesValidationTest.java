/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2026, University of Oxford.
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

import build.buf.gen.surimi.v1.SpeciesPrice;
import build.buf.gen.surimi.v1.UpdateSpeciesPricesRequest;
import eu.project.surimi.poseidon.scenarios.MinimalScenario;
import eu.project.surimi.poseidon.server.ServiceTest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.joda.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.agents.catches.CatchCategory;
import uk.ac.ox.poseidon.agents.market.BiomassMarket;
import uk.ac.ox.poseidon.agents.market.Price;
import uk.ac.ox.poseidon.biology.species.Species;

import java.util.Map;

import static eu.project.surimi.poseidon.server.prices.UpdateSpeciesPricesRequestHandler.getMarketsById;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.units.indriya.unit.Units.KILOGRAM;

class SetPricesValidationTest extends ServiceTest {

    private static final String MARKET_CODE = "M1";
    private static final String GEAR_CODE = "G1";
    private static final String SPECIES_CODE = "A";

    SetPricesValidationTest() {
        super(MinimalScenario.class);
    }

    private String simulationId;

    @BeforeEach
    void initSimulation() {
        simulationId = initialiseSimulation();
        step(simulationId);
    }

    @Test
    void stagedUpdateRejectedWhenGenericPriceExists() {
        final StatusRuntimeException error = assertThrows(
            StatusRuntimeException.class,
            () -> updatePrice(SPECIES_CODE, "juvenile", 2.0)
        );
        assertEquals(Status.INVALID_ARGUMENT.getCode(), error.getStatus().getCode());
    }

    @Test
    void genericUpdateRejectedWhenStagedPricesExist() {
        final BiomassMarket market = getMarketsById(simulationManager.getSimulation(simulationId))
            .get(MARKET_CODE);
        final CatchCategory catchCategory = new CatchCategory(GEAR_CODE);
        final Map<Species, Price> pricesBySpecies = market.getPrices().get(catchCategory);
        pricesBySpecies.remove(new Species(SPECIES_CODE, null, null));
        market.setPrice(
            catchCategory,
            new Species(SPECIES_CODE, "juvenile", null),
            new Price(Money.parse("GBP 1.0"), KILOGRAM)
        );

        final StatusRuntimeException error = assertThrows(
            StatusRuntimeException.class,
            () -> updatePrice(SPECIES_CODE, null, 2.0)
        );
        assertEquals(Status.INVALID_ARGUMENT.getCode(), error.getStatus().getCode());
    }

    private void updatePrice(
        final String speciesCode,
        final String lifeStage,
        final double price
    ) {
        speciesPriceConsumerStub.updateSpeciesPrices(
            UpdateSpeciesPricesRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .addPrices(
                    SpeciesPrice
                        .newBuilder()
                        .setMarketCode(MARKET_CODE)
                        .setCurrency("GBP")
                        .setGearCode(GEAR_CODE)
                        .setSpecies(toProtoSpecies(speciesCode, lifeStage))
                        .setPrice(price)
                        .build()
                )
                .build()
        );
    }

    private build.buf.gen.surimi.v1.Species toProtoSpecies(
        final String speciesCode,
        final String lifeStage
    ) {
        final build.buf.gen.surimi.v1.Species.Builder builder =
            build.buf.gen.surimi.v1.Species
                .newBuilder()
                .setSpeciesCode(speciesCode);
        if (lifeStage != null) {
            builder.setLifeStage(lifeStage);
        }
        return builder.build();
    }
}
