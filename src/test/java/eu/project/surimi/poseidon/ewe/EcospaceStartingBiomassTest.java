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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.geography.Envelope;
import uk.ac.ox.poseidon.geography.areas.CellAreaCalculator;

class EcospaceStartingBiomassTest {

  private static final double NAN = Double.NaN;

  // A 2 x 3 base map for Ecospace scenario 1: the top-right cell is land and
  // the bottom-right one is excluded, so the masked maps have two values in
  // the first row and three in the second, and there are four water cells.
  private static final double CELL_SIZE = 1.0;
  private static final EcospaceBasemap BASEMAP = new EcospaceBasemap(
    1,
    2,
    3,
    new Envelope(0, 3 * CELL_SIZE, -2 * CELL_SIZE, 0),
    new double[][] { { 10, 20, 0 }, { 30, 40, 50 } },
    new boolean[][] { { false, false, false }, { false, false, true } }
  );

  private static final String GROUPS_HEADER =
    "ScenarioID,GroupID,EcopathGroupID,CapacityCalType,IsMigratory," +
    "CapacityMap\n";
  private static final String HABITATS_HEADER =
    "ScenarioID,HabitatID,HabitatName,Sequence,HabitatMap\n";
  private static final String HABITATS =
    "1,1,All,0,\n" + "1,2,Sand,1,\"1,0.5;0.5,1,1;\"\n";
  private static final String GROUP_HABITATS_HEADER =
    "ScenarioID,GroupID,HabitatID,Preference\n";
  // A table without rows has no columns, so every preference table has one
  // row, for another Ecospace scenario
  private static final String OTHER_SCENARIO_PREFERENCE = "2,9,1,1\n";
  private static final String BIOMASSES = "GroupID,Biomass\n7,3\n8,-9999\n";
  private static final CellAreaCalculator TWO_SQUARE_KM = cell -> 2;
  private static final double KG_PER_CELL_AT_MEAN = 3 * 1000 * 2;

  // Boxed, so that NaN equals NaN
  private static List<List<Double>> rows(final double[][] map) {
    return Arrays.stream(map)
      .map(row -> Arrays.stream(row).boxed().toList())
      .toList();
  }

  private static Table table(final String csv) {
    return tableFromCsvString(csv).get(GLOBAL_SCOPE);
  }

  private static double[][] kilograms(
    final String groupRow,
    final String groupHabitats
  ) {
    final ImmutableMap<Integer, EcospaceGroup> groups =
      EcospaceGroups.fromTable(table(GROUPS_HEADER + groupRow), BASEMAP);
    return EcospaceStartingBiomass.kilogramsPerCell(
      BASEMAP,
      groups,
      EcospaceHabitats.fromTables(
        table(HABITATS_HEADER + HABITATS),
        table(
          GROUP_HABITATS_HEADER + OTHER_SCENARIO_PREFERENCE + groupHabitats
        ),
        BASEMAP
      ),
      EcopathBiomasses.fromTable(table(BIOMASSES)),
      TWO_SQUARE_KM,
      List.copyOf(groups.keySet())
    ).get(groups.keySet().iterator().next());
  }

  @Test
  void withoutACapacityMapEveryWaterCellGetsTheMeanDensity() {
    assertThat(rows(kilograms("1,4,7,0,0,\n", ""))).isEqualTo(
      rows(new double[][] {
        { KG_PER_CELL_AT_MEAN, KG_PER_CELL_AT_MEAN, NAN },
        { KG_PER_CELL_AT_MEAN, KG_PER_CELL_AT_MEAN, NAN }
      })
    );
  }

  // Normalised capacities 1, 0.5, 1 and 0.5 sum to 3 over 4 water cells, so a
  // cell gets 4 / 3 of the mean density times its capacity.
  @Test
  void theCapacityMapShapesTheSpreadAndKeepsTheMeanDensity() {
    assertThat(rows(kilograms("1,4,7,0,0,\"2,1;2,1,0;\"\n", ""))).isEqualTo(
      rows(new double[][] {
        { (KG_PER_CELL_AT_MEAN * 4) / 3, (KG_PER_CELL_AT_MEAN * 2) / 3, NAN },
        { (KG_PER_CELL_AT_MEAN * 4) / 3, (KG_PER_CELL_AT_MEAN * 2) / 3, NAN }
      })
    );
  }

  @Test
  void aGroupUsingHabitatsIsSpreadByThoseItPrefers() {
    assertThat(rows(kilograms("1,4,7,2,0,\n", "1,4,2,1\n"))).isEqualTo(
      rows(new double[][] {
        { (KG_PER_CELL_AT_MEAN * 4) / 3, (KG_PER_CELL_AT_MEAN * 2) / 3, NAN },
        { (KG_PER_CELL_AT_MEAN * 2) / 3, (KG_PER_CELL_AT_MEAN * 4) / 3, NAN }
      })
    );
  }

  @Test
  void habitatsAreIgnoredWithoutTheHabitatFlag() {
    assertThat(kilograms("1,4,7,0,0,\n", "1,4,2,1\n")[0][1]).isEqualTo(
      KG_PER_CELL_AT_MEAN
    );
  }

  @Test
  void aGroupPreferringTheFirstHabitatUsesTheWholeMap() {
    assertThat(kilograms("1,4,7,2,0,\n", "1,4,1,1\n")[0][1]).isEqualTo(
      KG_PER_CELL_AT_MEAN
    );
  }

  @Test
  void refusesAGroupPreferringTheFirstHabitatAndOthers() {
    assertThatThrownBy(() -> kilograms("1,4,7,2,0,\n", "1,4,1,1\n1,4,2,1\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("depends on the order");
  }

  @Test
  void refusesEnvironmentalResponses() {
    assertThatThrownBy(() -> kilograms("1,4,7,1,0,\n", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("environmental responses");
  }

  @Test
  void refusesAMigratoryGroup() {
    assertThatThrownBy(() -> kilograms("1,4,7,0,1,\n", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("migrates");
  }

  @Test
  void refusesACellThatEcospaceWouldAdjust() {
    assertThatThrownBy(() -> kilograms("1,4,7,0,0,\"1,1;1,0.001,1;\"\n", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Ecospace would adjust it");
  }

  @Test
  void refusesAnUnknownCapacityCalculationType() {
    assertThatThrownBy(() -> kilograms("1,4,7,4,0,\n", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unknown capacity calculation type 4");
  }

  @Test
  void refusesAGroupWithoutEnteredBiomass() {
    assertThatThrownBy(() -> kilograms("1,4,8,0,0,\n", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No biomass entered for Ecopath group 8");
  }

  @Test
  void aCapacityMapPositiveInNoWaterCellCountsAsOneEverywhere() {
    // Only the excluded cell, which is not water, has a positive capacity
    assertThat(rows(kilograms("1,4,7,0,0,\"0,0;0,0,7;\"\n", ""))).isEqualTo(
      rows(new double[][] {
        { KG_PER_CELL_AT_MEAN, KG_PER_CELL_AT_MEAN, NAN },
        { KG_PER_CELL_AT_MEAN, KG_PER_CELL_AT_MEAN, NAN }
      })
    );
  }

  @Test
  void refusesAGroupWhoseHabitatsGiveItNoCapacity() {
    assertThatThrownBy(() -> kilograms("1,4,7,2,0,\n", "1,4,1,0\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not positive in any water cell");
  }
}
