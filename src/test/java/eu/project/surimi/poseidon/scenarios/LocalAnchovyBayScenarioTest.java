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

package eu.project.surimi.poseidon.scenarios;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import eu.project.surimi.poseidon.scenarios.anchovybay.LocalAnchovyBayScenario;
import java.nio.file.Path;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.biology.biomass.BiomassGrid;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.fishing.market.BiomassMarket;
import uk.ac.ox.poseidon.fishing.market.MarketGrid;

// The local scenario plays the pipeline's monthly biomass maps and prices in
// place of the controller's messages. Compare against pipeline files, whose
// contents can change with revisions of the model.
class LocalAnchovyBayScenarioTest {

  private static final Path INPUT_PATH = Path.of("inputs", "anchovy_bay");

  private Simulation simulation;

  @BeforeEach
  void startSimulation() {
    simulation = new LocalAnchovyBayScenario().get().startNewSimulation(0);
  }

  @AfterEach
  void finishSimulation() {
    simulation.finish();
  }

  @Test
  void biomassChangesMonthByMonth() {
    final List<Map<Species, Double>> monthlyBiomass = new ArrayList<>();
    for (int month = 0; month < 3; month++) {
      simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
      monthlyBiomass.add(biomassBySpecies());
    }

    assertThat(monthlyBiomass).allSatisfy(biomass ->
      assertThat(
        biomass.values().stream().mapToDouble(Double::doubleValue).sum()
      ).isPositive()
    );
    for (int month = 1; month < monthlyBiomass.size(); month++) {
      assertThat(monthlyBiomass.get(month))
        .as("biomass in month %d", month)
        .isNotEqualTo(monthlyBiomass.get(month - 1));
    }
  }

  @Test
  void marketsHoldThePipelinePrices() {
    simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));

    final List<Tuple> marketPrices = new ArrayList<>();
    simulation
      .getComponent(MarketGrid.class)
      .stream()
      .map(BiomassMarket.class::cast)
      .forEach(market ->
        market.forEachPrice((category, species, price) ->
          marketPrices.add(
            tuple(
              market.getCode(),
              species.getCode(),
              category.getCode(),
              price.valueForKgDouble(1.0)
            )
          )
        )
      );

    assertThat(marketPrices).containsExactlyInAnyOrderElementsOf(
      Table.read()
        .csv(INPUT_PATH.resolve("prices.csv").toFile())
        .stream()
        .map(row ->
          tuple(
            row.getString("market_code"),
            row.getString("species_code"),
            row.getString("category_code"),
            row.getDouble("price")
          )
        )
        .toList()
    );
  }

  private Map<Species, Double> biomassBySpecies() {
    return simulation
      .getComponents(BiomassGrid.class)
      .stream()
      .collect(toMap(BiomassGrid::getSpecies, BiomassGrid::getSum));
  }
}
