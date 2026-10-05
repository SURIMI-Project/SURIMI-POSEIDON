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

package eu.project.surimi.poseidon.scenarios;

import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.agents.market.BiomassMarket;
import uk.ac.ox.poseidon.agents.market.MarketGrid;
import uk.ac.ox.poseidon.biology.biomass.BiomassGrid;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ox.poseidon.core.time.Factories.dateTime;

/**
 * Checks where the Northwestern Mediterranean scenario gets its biomass and prices from: the
 * scenario served to SURIMI must leave them to the gRPC messages, while the local scenario reads
 * them from its data files. Runs start in 2015, when both {@code biomass_grids.nc} and
 * {@code prices.csv} have data.
 */
class NorthwesternMedInputDriversTest {

    private static final LocalDate START_DATE = LocalDate.of(2015, 1, 1);

    @Test
    void servedScenarioReadsNoLocalBiomassOrPrices() {
        final Simulation simulation = runOneMonth(new NorthwesternMedScenario().get());
        assertThat(totalBiomass(simulation)).isZero();
        assertThat(markets(simulation)).allMatch(market -> market.getPrices().isEmpty());
    }

    @Test
    void localScenarioReadsBiomassAndPrices() {
        final Simulation simulation = runOneMonth(new LocalNorthwesternMedScenario().get());
        assertThat(totalBiomass(simulation)).isPositive();
        assertThat(markets(simulation)).anyMatch(market -> !market.getPrices().isEmpty());
    }

    private static Simulation runOneMonth(final Scenario scenario) {
        scenario.setStartingDateTime(dateTime(START_DATE.atStartOfDay()));
        final Simulation simulation = scenario.startNewSimulation();
        simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
        simulation.finish();
        return simulation;
    }

    private static double totalBiomass(final Simulation simulation) {
        return simulation
            .getComponents(BiomassGrid.class)
            .stream()
            .mapToDouble(BiomassGrid::getSum)
            .sum();
    }

    private static List<BiomassMarket> markets(final Simulation simulation) {
        final List<BiomassMarket> markets = simulation
            .getComponent(MarketGrid.class)
            .stream()
            .filter(BiomassMarket.class::isInstance)
            .map(BiomassMarket.class::cast)
            .toList();
        assertThat(markets).isNotEmpty();
        return markets;
    }
}
