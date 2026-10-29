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

import static eu.project.surimi.poseidon.ewe.EcospaceBasemap.DEGREE_TO_KM;
import static eu.project.surimi.poseidon.ewe.EcospaceBasemap.fromTable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.geography.Envelope;

class EcospaceBasemapTest {

  private static final String HEADER =
    "ScenarioName,Inrow,Incol,CellLength,MinLat,MinLon,AssumeSquareCells,DepthMap,ScenarioID\n";
  private static final String BAY =
    "Bay,2,3,20,10,5,False,\"0,0,3;4,5.5,490;\",1\n";
  private static final String COVE = "Cove,1,1,10,,,False,\"7;\",2\n";

  private static Table table(final String... rows) {
    return tableFromCsvString(HEADER + String.join("", rows)).get(GLOBAL_SCOPE);
  }

  private static void assertEnvelope(
    final Envelope actual,
    final double west,
    final double east,
    final double south,
    final double north
  ) {
    assertThat(actual.getMinX()).isCloseTo(west, within(1e-12));
    assertThat(actual.getMaxX()).isCloseTo(east, within(1e-12));
    assertThat(actual.getMinY()).isCloseTo(south, within(1e-12));
    assertThat(actual.getMaxY()).isCloseTo(north, within(1e-12));
  }

  @Test
  void readsTheSelectedScenarioFromAnyTable() {
    final Table table = table(COVE, BAY);
    // Tablesaw infers integer columns from a CSV table, where an .eiixml one
    // has longs
    assertThat(table.column("Inrow")).isInstanceOf(IntColumn.class);
    final EcospaceBasemap basemap = fromTable(table, "Bay");
    final double cellSize = 20 / DEGREE_TO_KM;
    assertThat(basemap.getScenarioId()).isEqualTo(1);
    assertThat(basemap.getRows()).isEqualTo(2);
    assertThat(basemap.getCols()).isEqualTo(3);
    assertEnvelope(
      basemap.getEnvelope(),
      5,
      5 + 3 * cellSize,
      10 - 2 * cellSize,
      10
    );
    assertThat(basemap.getDepth(0, 2)).isEqualTo(3);
    assertThat(basemap.getDepth(1, 1)).isEqualTo(5.5);
    assertThat(basemap.getDepth(1, 2)).isEqualTo(490);
  }

  @Test
  void placesTheTopLeftCornerAtZeroWhenMinLatAndMinLonAreMissing() {
    final EcospaceBasemap basemap = fromTable(table(BAY, COVE), "Cove");
    final double cellSize = 10 / DEGREE_TO_KM;
    assertEnvelope(basemap.getEnvelope(), 0, cellSize, -cellSize, 0);
  }

  @Test
  void listsTheAvailableScenariosWhenTheNameIsUnknown() {
    assertThatThrownBy(() -> fromTable(table(BAY, COVE), "Lagoon"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Lagoon")
      .hasMessageContaining("Bay")
      .hasMessageContaining("Cove");
  }

  @Test
  void rejectsDuplicateScenarioNames() {
    assertThatThrownBy(() -> fromTable(table(BAY, BAY), "Bay"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("More than one");
  }

  @Test
  void rejectsSquareCellsWhetherSetMissingOrNumeric() {
    assertThatThrownBy(() ->
      fromTable(table(BAY.replace("False", "True")), "Bay")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("AssumeSquareCells");
    assertThatThrownBy(() ->
      fromTable(table(BAY.replace("False", ""), COVE), "Bay")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("AssumeSquareCells");
    // Access stores true as -1
    final Table numeric = table(
      BAY.replace("False", "-1"),
      COVE.replace("False", "0")
    );
    assertThat(numeric.column("AssumeSquareCells")).isInstanceOf(
      IntColumn.class
    );
    assertThatThrownBy(() -> fromTable(numeric, "Bay"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("AssumeSquareCells");
    assertThat(fromTable(numeric, "Cove").getRows()).isEqualTo(1);
  }

  @Test
  void rejectsDimensionsThatAreNotPositiveWholeNumbers() {
    assertThatThrownBy(() ->
      fromTable(table(BAY.replace("Bay,2,", "Bay,0,")), "Bay")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Inrow");
    assertThatThrownBy(() ->
      fromTable(table(BAY.replace("Bay,2,3,", "Bay,2,3.5,")), "Bay")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Incol");
  }

  @Test
  void rejectsACellLengthThatIsNotPositive() {
    assertThatThrownBy(() ->
      fromTable(table(BAY.replace(",20,", ",0,")), "Bay")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("CellLength");
  }

  @Test
  void rejectsADepthMapThatDoesNotMatchTheDimensions() {
    assertThatThrownBy(() ->
      fromTable(table(BAY.replace("Bay,2,", "Bay,3,")), "Bay")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Expected 3 rows");
  }

  @Test
  void rejectsAMissingScenarioId() {
    assertThatThrownBy(() ->
      fromTable(table(BAY.replace(",1\n", ",\n")), "Bay")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("ScenarioID");
  }

  @Test
  void rejectsATableWithoutADepthMap() {
    final Table table = table(BAY).removeColumns("DepthMap");
    assertThatThrownBy(() -> fromTable(table, "Bay"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("no DepthMap column");
  }

  private static Table tableWithExclusions(
    final String bayExclusionMap,
    final String coveExclusionMap
  ) {
    return tableFromCsvString(
      HEADER.replace("\n", ",ExclusionMap\n") +
        BAY.replace("\n", "," + bayExclusionMap + "\n") +
        COVE.replace("\n", "," + coveExclusionMap + "\n")
    ).get(GLOBAL_SCOPE);
  }

  @Test
  void readsTheExclusionMap() {
    final Table table = tableWithExclusions("\"0,1,0;;\"", "");
    final EcospaceBasemap bay = fromTable(table, "Bay");
    assertThat(bay.isExcluded(0, 1)).isTrue();
    assertThat(bay.isExcluded(0, 0)).isFalse();
    assertThat(bay.isExcluded(0, 2)).isFalse();
    assertThat(bay.isExcluded(1, 0)).isFalse();
    // A missing value excludes no cells
    assertThat(fromTable(table, "Cove").isExcluded(0, 0)).isFalse();
  }

  @Test
  void excludesNoCellsWithoutAnExclusionMapColumn() {
    final EcospaceBasemap basemap = fromTable(table(BAY), "Bay");
    for (int row = 0; row < 2; row++) {
      for (int col = 0; col < 3; col++) {
        assertThat(basemap.isExcluded(row, col)).isFalse();
      }
    }
  }

  @Test
  void rejectsAnExclusionMapThatDoesNotMatchTheDimensions() {
    assertThatThrownBy(() ->
      fromTable(tableWithExclusions("\"0,1;;\"", ""), "Bay")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Expected 3 values in row 0");
  }
}
