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
import static org.assertj.core.api.Assertions.tuple;

import com.google.common.collect.ImmutableList;
import eu.project.surimi.poseidon.ewe.EcopathGroups;
import eu.project.surimi.poseidon.ewe.EcospaceBasemap;
import eu.project.surimi.poseidon.ewe.EcospacePortCells;
import eu.project.surimi.poseidon.ewe.EweDatabase;
import eu.project.surimi.poseidon.scenarios.anchovybay.AnchovyBayScenario;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.biology.species.Species;
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
// The Anchovy Bay models are preliminary and keep changing, so the tests compare
// the simulation with what the EwE tables say, never with their current content.
class AnchovyBayScenarioTest {

  private static final Path INPUT_PATH = Path.of("inputs", "anchovy_bay");
  private static final String ECOSPACE_SCENARIO = "BayOfAnchovies";

  private Simulation simulation;

  private static EweDatabase eweDatabase(final String fileName) {
    return EweDatabase.open(INPUT_PATH.resolve(fileName));
  }

  /** The database the scenario reads. */
  private static EweDatabase scenarioDatabase() {
    return eweDatabase("anchovy_bay.sqlite");
  }

  private static EcospaceBasemap ecospaceBasemap(
    final EweDatabase eweDatabase
  ) {
    return EcospaceBasemap.fromTable(
      eweDatabase.table(EcospaceBasemap.TABLE_NAME),
      ECOSPACE_SCENARIO
    );
  }

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
    final EcospaceBasemap ecospaceBasemap = ecospaceBasemap(scenarioDatabase());
    assertThat(modelGrid.getGridWidth()).isEqualTo(ecospaceBasemap.getCols());
    assertThat(modelGrid.getGridHeight()).isEqualTo(ecospaceBasemap.getRows());
    assertThat(modelGrid.getEnvelope()).isEqualTo(
      ecospaceBasemap.getEnvelope()
    );
  }

  @Test
  void hasTheEcospaceBathymetry() {
    final BathymetricGrid bathymetricGrid = simulation.getComponent(
      BathymetricGrid.class
    );
    final EcospaceBasemap ecospaceBasemap = ecospaceBasemap(scenarioDatabase());
    final int cols = ecospaceBasemap.getCols();
    // EwE's row and column are the cell's y and x
    assertThat(range(0, ecospaceBasemap.getRows() * cols).boxed()).allSatisfy(
      i -> {
        final int row = i / cols;
        final int col = i % cols;
        assertThat(bathymetricGrid.getDepth(new Cell(col, row)))
          .as("depth at row %d, column %d", row, col)
          .isEqualTo(ecospaceBasemap.getDepth(row, col));
      }
    );
  }

  @Test
  void hasAPortOnEachEcospacePortCell() {
    final PortGrid portGrid = simulation.getComponent(PortGrid.class);
    final Map<String, Cell> cellsByCode = portGrid
      .getPorts()
      .collect(Collectors.toMap(Port::getCode, portGrid::getCell));
    final EweDatabase eweDatabase = scenarioDatabase();
    // In row-major order, as the scenario codes them
    final ImmutableList<Cell> portCells = EcospacePortCells.fromTable(
      eweDatabase.table(EcospacePortCells.TABLE_NAME),
      ecospaceBasemap(eweDatabase)
    ).asList();
    assertThat(cellsByCode).isEqualTo(
      range(0, portCells.size())
        .boxed()
        .collect(Collectors.toMap(i -> "P" + i, portCells::get))
    );
  }

  // The .eiixml and .sqlite files are different saves of the model, so only
  // what they still share is compared: the base map, and the groups' IDs and
  // names. Their ports and some Ecopath parameters differ.
  @Test
  void readsTheSameBasemapAndGroupsFromBothFormats() {
    final EweDatabase sqlite = scenarioDatabase();
    final EweDatabase eiiXml = eweDatabase("anchovy_bay.eiixml");
    final EcospaceBasemap sqliteBasemap = ecospaceBasemap(sqlite);
    final EcospaceBasemap eiiXmlBasemap = ecospaceBasemap(eiiXml);
    assertThat(sqliteBasemap.getScenarioId()).isEqualTo(
      eiiXmlBasemap.getScenarioId()
    );
    assertThat(sqliteBasemap.getRows()).isEqualTo(eiiXmlBasemap.getRows());
    assertThat(sqliteBasemap.getCols()).isEqualTo(eiiXmlBasemap.getCols());
    assertThat(sqliteBasemap.getEnvelope()).isEqualTo(
      eiiXmlBasemap.getEnvelope()
    );
    final int cols = sqliteBasemap.getCols();
    assertThat(range(0, sqliteBasemap.getRows() * cols).boxed()).allSatisfy(
      i -> {
        final int row = i / cols;
        final int col = i % cols;
        assertThat(sqliteBasemap.getDepth(row, col))
          .as("depth at row %d, column %d", row, col)
          .isEqualTo(eiiXmlBasemap.getDepth(row, col));
        assertThat(sqliteBasemap.isExcluded(row, col))
          .as("exclusion at row %d, column %d", row, col)
          .isEqualTo(eiiXmlBasemap.isExcluded(row, col));
      }
    );
    assertThat(
      EcopathGroups.fromTable(sqlite.table(EcopathGroups.TABLE_NAME))
    ).isEqualTo(
      EcopathGroups.fromTable(eiiXml.table(EcopathGroups.TABLE_NAME))
    );
  }

  @Test
  void hasASpeciesPerEcopathGroup() {
    assertThat(simulation.getComponents(Species.class))
      .extracting(Species::getCode, Species::getName, Species::getLifeStage)
      .containsExactlyInAnyOrderElementsOf(
        EcopathGroups.fromTable(
          scenarioDatabase().table(EcopathGroups.TABLE_NAME)
        )
          .stream()
          .map(group ->
            tuple(String.valueOf(group.getId()), group.getName(), null)
          )
          .toList()
      );
  }
}
