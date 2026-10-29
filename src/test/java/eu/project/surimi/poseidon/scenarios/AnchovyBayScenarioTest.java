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

import static eu.project.surimi.poseidon.scenarios.anchovybay.AnchovyBayScenario.START_DATE;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import eu.project.surimi.poseidon.scenarios.anchovybay.AnchovyBayScenario;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.grids.Cell;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;
import uk.ac.ox.poseidon.geography.ports.Port;
import uk.ac.ox.poseidon.geography.ports.PortGrid;
import uk.ac.ox.poseidon.io.ScenarioLoader;
import uk.ac.ox.poseidon.io.ScenarioWriter;

// The scenario goes through YAML and back, as the generated
// scenarios/anchovy_bay.yaml does, so the EwE factories must survive that trip.
class AnchovyBayScenarioTest {

  private Simulation simulation;

  @BeforeEach
  void startSimulation() {
    final String yaml = new ScenarioWriter().write(
      new AnchovyBayScenario().get()
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
  void startsAtTheStartDate() {
    assertThat(simulation.getTemporalSchedule().getDateTime()).isEqualTo(
      START_DATE.atStartOfDay()
    );
  }

  @Test
  void hasTheEcospaceGrid() {
    final ModelGrid modelGrid = simulation.getComponent(ModelGrid.class);
    assertThat(modelGrid.getGridWidth()).isEqualTo(20);
    assertThat(modelGrid.getGridHeight()).isEqualTo(20);
  }

  @Test
  void hasTheEcospaceBathymetry() {
    final BathymetricGrid bathymetricGrid = simulation.getComponent(
      BathymetricGrid.class
    );
    assertThat(range(0, 20).mapToObj(x -> new Cell(x, 0))).allMatch(
      bathymetricGrid::isLand
    );
    assertThat(bathymetricGrid.getDepth(new Cell(19, 19))).isEqualTo(490);
  }

  @Test
  void hasAPortOnEachEcospacePortCell() {
    final PortGrid portGrid = simulation.getComponent(PortGrid.class);
    final Map<String, Cell> cellsByCode = portGrid
      .getPorts()
      .collect(Collectors.toMap(Port::getCode, portGrid::getCell));
    assertThat(cellsByCode).containsOnly(
      entry("P0", new Cell(7, 1)),
      entry("P1", new Cell(19, 7)),
      entry("P2", new Cell(0, 12))
    );
  }
}
