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

package eu.project.surimi.poseidon.ewe;

import static eu.project.surimi.poseidon.ewe.EcospacePortCells.fromTable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.geography.Envelope;
import uk.ac.ox.poseidon.geography.grids.Cell;

class EcospacePortCellsTest {

  private static final String HEADER = "EcopathFleetID,ScenarioID,PortMap\n";

  // A 2 x 3 base map, for Ecospace scenario 1
  private static final EcospaceBasemap BASEMAP = new EcospaceBasemap(
    1,
    2,
    3,
    new Envelope(0, 3, -2, 0),
    new double[2][3],
    new boolean[2][3]
  );

  private static Table table(final String... rows) {
    return tableFromCsvString(HEADER + String.join("", rows)).get(GLOBAL_SCOPE);
  }

  @Test
  void unitesTheFleetsOfTheScenarioInRowMajorOrder() {
    final Table table = table(
      "1,1,\";0,0,1;\"\n",
      "2,1,\"1,0,0;0,0,1;\"\n",
      "1,2,\"0,1,0;;\"\n"
    );
    assertThat(fromTable(table, BASEMAP)).containsExactly(
      new Cell(0, 0),
      new Cell(2, 1)
    );
  }

  @Test
  void aMissingPortMapIsAFleetWithoutPorts() {
    final Table table = table("1,1,\n", "2,1,\";1,0,0;\"\n");
    assertThat(fromTable(table, BASEMAP)).containsExactly(new Cell(0, 1));
  }

  @Test
  void rejectsAScenarioWithoutPortCells() {
    final Table table = table("1,1,\";;\"\n", "1,2,\"0,1,0;;\"\n");
    assertThatThrownBy(() -> fromTable(table, BASEMAP))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No port cells for Ecospace scenario 1");
  }

  @Test
  void rejectsAPortMapThatDoesNotMatchTheBasemap() {
    final Table table = table("1,1,\"0,0,1;\"\n");
    assertThatThrownBy(() -> fromTable(table, BASEMAP))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Expected 2 rows");
  }
}
