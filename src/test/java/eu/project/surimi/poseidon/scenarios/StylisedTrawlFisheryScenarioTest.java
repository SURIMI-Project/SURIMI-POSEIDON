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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingDouble;
import static org.assertj.core.api.Assertions.assertThat;

import eu.project.surimi.poseidon.scenarios.stylisedtrawl.StylisedTrawlFisheryScenario;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Period;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.biology.biomass.BiomassGrid;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.fishing.market.BiomassSaleAccumulator;
import uk.ac.ox.poseidon.fishing.vessels.Fleet;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;
import uk.ac.ox.poseidon.io.ScenarioLoader;
import uk.ac.ox.poseidon.io.ScenarioWriter;

// The scenario goes through YAML and back, so that its factories must survive
// that trip, even though it is only run locally.
class StylisedTrawlFisheryScenarioTest {

  private Simulation simulation;

  @BeforeEach
  void startSimulation() {
    final String yaml = new ScenarioWriter().write(
      new StylisedTrawlFisheryScenario().get()
    );
    simulation = new ScenarioLoader("eu.project.surimi")
      .load(yaml)
      .startNewSimulation();
  }

  @AfterEach
  void finishSimulation() {
    simulation.finish();
  }

  @Test
  void hasOnlyDemersalFishBiomass() {
    final var biomassGrids = simulation.getComponents(BiomassGrid.class);
    assertThat(biomassGrids)
      .extracting(grid -> grid.getSpecies().getCode())
      .containsExactly("DEM");
    assertThat(biomassGrids).allSatisfy(grid ->
      assertThat(grid.getSum()).isPositive()
    );
  }

  @Test
  void landsDemersalFishInAMonth() {
    simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
    final Map<String, Double> landedKgBySpecies = simulation
      .getComponent(BiomassSaleAccumulator.class)
      .getEvents()
      .flatMap(sale -> sale.getItems().stream())
      .collect(
        groupingBy(
          item -> item.getSpecies().getCode(),
          summingDouble(item -> item.getContent().asBiomass().asKg())
        )
      );
    assertThat(landedKgBySpecies).containsOnlyKeys("DEM");
    assertThat(landedKgBySpecies.values()).allSatisfy(kg ->
      assertThat(kg).isPositive()
    );
  }

  @Test
  void hasOnlyTrawlers() {
    // The register's first entries apply once the simulation steps
    simulation.getTemporalSchedule().stepFor(simulation, Period.ofDays(1));
    assertThat(simulation.getComponent(Fleet.class).getVessels())
      .isNotEmpty()
      .allSatisfy(vessel ->
        assertThat(vessel.getAttributes()).containsEntry(
          "main_fishing_gear",
          "OTB"
        )
      );
  }

  // The French EEZ is not part of the model: not closed to fishing, but left
  // out of the grid, so that its fish are neither unfished nor on the map
  @Test
  void hasNoFrenchEezCells() throws IOException {
    final var rows = Files.readAllLines(
      Path.of("inputs", "northwestern_med", "french_eez.asc")
    )
      .stream()
      .skip(6)
      .map(line -> line.trim().split("\\s+"))
      .toList();
    final var modelGrid = simulation.getComponent(ModelGrid.class);
    assertThat(modelGrid.getActiveCells()).isNotEmpty();
    assertThat(modelGrid.getActiveCells()).noneMatch(cell ->
      rows.get(cell.getY())[cell.getX()].equals("1")
    );
  }
}
