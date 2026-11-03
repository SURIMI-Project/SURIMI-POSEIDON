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
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.geography.Envelope;

class EcospaceGroupsTest {

  // A 2 x 3 base map for Ecospace scenario 1, whose top-right cell is land,
  // so that its masked maps have two values in the first row and three in the
  // second
  private static final EcospaceBasemap BASEMAP = new EcospaceBasemap(
    1,
    2,
    3,
    new Envelope(0, 3, -2, 0),
    new double[][] { { 10, 20, 0 }, { 30, 40, 50 } },
    new boolean[][] { { false, false, false }, { false, false, true } }
  );

  private static final String HEADER =
    "ScenarioID,GroupID,EcopathGroupID,CapacityCalType,IsMigratory," +
    "CapacityMap\n";

  private static ImmutableMap<Integer, EcospaceGroup> groups(
    final String rows
  ) {
    return EcospaceGroups.fromTable(
      tableFromCsvString(HEADER + rows).get(GLOBAL_SCOPE),
      BASEMAP
    );
  }

  @Test
  void readsTheGroupsOfTheScenarioByEcopathGroupId() {
    final EcospaceGroup group = groups("1,3,7,3,1,\"1,2;3,4,5;\"\n").get(7);
    assertThat(group.getEcospaceGroupId()).isEqualTo(3);
    assertThat(group.getEcopathGroupId()).isEqualTo(7);
    assertThat(group.isUsingHabitats()).isTrue();
    assertThat(group.isUsingEnvironmentalResponses()).isTrue();
    assertThat(group.isMigratory()).isTrue();
    assertThat(group.getCapacityInput(0, 1)).isEqualTo(2);
    assertThat(group.getCapacityInput(1, 2)).isEqualTo(5);
    assertThat(group.getCapacityInput(0, 2)).isNaN();
  }

  @Test
  void aMissingCapacityMapIsOneInEveryCellOfPositiveDepth() {
    final EcospaceGroup group = groups("1,1,4,0,0,\n").get(4);
    assertThat(group.getCapacityInput(0, 0)).isEqualTo(1);
    assertThat(group.getCapacityInput(1, 2)).isEqualTo(1);
    assertThat(group.getCapacityInput(0, 2)).isNaN();
  }

  @Test
  void ignoresRowsOfOtherScenariosAndRowsWithoutAScenario() {
    assertThat(groups("2,9,9,0,0,\n,8,8,0,0,\n1,1,4,0,0,\n")).containsOnlyKeys(
      4
    );
  }

  @Test
  void rejectsAScenarioWithoutGroups() {
    assertThatThrownBy(() -> groups("2,1,4,0,0,\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No groups for Ecospace scenario 1");
  }

  @Test
  void rejectsACapacityCalculationTypeThatIsNotAWholeNumber() {
    assertThatThrownBy(() -> groups("1,1,4,1.5,0,\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be a non-negative whole number");
    assertThatThrownBy(() -> groups("1,1,4,-1,0,\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be a non-negative whole number");
  }

  @Test
  void rejectsAMissingMigratoryFlag() {
    assertThatThrownBy(() -> groups("1,1,4,0,,\n1,2,5,0,0,\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No value in column IsMigratory");
  }

  @Test
  void rejectsTwoGroupsOfTheSameEcopathGroup() {
    assertThatThrownBy(() -> groups("1,1,4,0,0,\n1,2,4,0,0,\n")).isInstanceOf(
      IllegalArgumentException.class
    );
  }

  @Test
  void aGroupNeedsPositiveIds() {
    final double[][] capacityInput = { { 1 } };
    assertThatThrownBy(() -> new EcospaceGroup(0, 4, 0, false, capacityInput))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Group IDs must be positive");
    assertThatThrownBy(() -> new EcospaceGroup(1, 0, 0, false, capacityInput))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Group IDs must be positive");
  }
}
