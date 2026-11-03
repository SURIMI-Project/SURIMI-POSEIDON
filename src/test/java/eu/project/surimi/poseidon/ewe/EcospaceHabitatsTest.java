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

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.geography.Envelope;

class EcospaceHabitatsTest {

  private static final double NAN = Double.NaN;

  // A 2 x 3 base map for Ecospace scenario 1, whose top-right cell is land,
  // so that its masked maps have two values in the first row and three in the
  // second
  private static final EcospaceBasemap BASEMAP = new EcospaceBasemap(
    1,
    2,
    3,
    new Envelope(0, 3, -2, 0),
    new double[][] { { 10, 20, 0 }, { 30, 40, 50 } },
    new boolean[][] { { false, false, false }, { false, false, false } }
  );

  private static final String HABITATS_HEADER =
    "ScenarioID,HabitatID,HabitatName,Sequence,HabitatMap\n";
  private static final String HABITATS =
    "1,1,All,0,\n" + "1,2,Sand,1,\"1,0.5;0.5,1,1;\"\n" + "1,3,Reef,2,\n";
  private static final String PREFERENCES_HEADER =
    "ScenarioID,GroupID,HabitatID,Preference\n";
  // A table without rows has no columns, so every preference table has one
  // row, for another Ecospace scenario
  private static final String OTHER_SCENARIO_PREFERENCE = "2,9,1,1\n";

  // Boxed, so that NaN equals NaN
  private static List<List<Double>> rows(final double[][] map) {
    return Arrays.stream(map)
      .map(row -> Arrays.stream(row).boxed().toList())
      .toList();
  }

  private static EcospaceHabitats habitats(
    final String habitats,
    final String preferences
  ) {
    return EcospaceHabitats.fromTables(
      tableFromCsvString(HABITATS_HEADER + habitats).get(GLOBAL_SCOPE),
      tableFromCsvString(
        PREFERENCES_HEADER + OTHER_SCENARIO_PREFERENCE + preferences
      ).get(GLOBAL_SCOPE),
      BASEMAP
    );
  }

  private static EcospaceHabitats habitats(final String preferences) {
    return habitats(HABITATS, preferences);
  }

  @Test
  void aGroupUsesEachCellAsMuchAsThePreferredHabitatsCoverIt() {
    assertThat(
      rows(habitats("1,4,2,1\n1,4,3,0.5\n").habitatUse(4).orElseThrow())
    ).isEqualTo(rows(new double[][] { { 1, 0.5, NAN }, { 0.5, 1, 1 } }));
  }

  @Test
  void aMissingPreferenceCountsAsOne() {
    assertThat(
      rows(habitats("1,4,2,\n").habitatUse(4).orElseThrow())
    ).isEqualTo(rows(new double[][] { { 1, 0.5, NAN }, { 0.5, 1, 1 } }));
  }

  @Test
  void aMissingHabitatMapCoversNoCell() {
    assertThat(
      rows(habitats("1,4,3,1\n").habitatUse(4).orElseThrow())
    ).isEqualTo(rows(new double[][] { { 0, 0, NAN }, { 0, 0, 0 } }));
  }

  @Test
  void aGroupWithoutPreferencesUsesTheWholeMap() {
    assertThat(habitats("1,5,2,1\n").habitatUse(4)).isEmpty();
  }

  @Test
  void aGroupPreferringTheFirstHabitatUsesTheWholeMap() {
    assertThat(habitats("1,4,1,1\n").habitatUse(4)).isEmpty();
  }

  @Test
  void aGroupWhoseOnlyPreferenceIsTheFirstHabitatWithZeroUsesNothing() {
    assertThat(
      rows(habitats("1,4,1,0\n").habitatUse(4).orElseThrow())
    ).isEqualTo(rows(new double[][] { { 0, 0, NAN }, { 0, 0, 0 } }));
  }

  @Test
  void refusesAGroupPreferringTheFirstHabitatAndOthers() {
    final EcospaceHabitats habitats = habitats("1,4,1,1\n1,4,2,1\n");
    assertThatThrownBy(() -> habitats.habitatUse(4))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("depends on the order");
  }

  @Test
  void ignoresRowsOfOtherScenariosAndRowsWithoutAScenario() {
    final EcospaceHabitats habitats = habitats(
      HABITATS + "2,4,Rocky,3,\n,5,Kelp,4,\n",
      "1,4,2,1\n,4,99,1\n2,4,4,1\n"
    );
    assertThat(habitats.habitatUse(4)).isPresent();
  }

  @Test
  void rejectsAScenarioWithoutHabitats() {
    assertThatThrownBy(() -> habitats("2,1,All,0,\n", ""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No habitats for Ecospace scenario 1");
  }

  @Test
  void rejectsAPreferenceForAHabitatTheScenarioDoesNotHave() {
    assertThatThrownBy(() -> habitats("1,4,5,1\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("prefers habitat 5, which the Ecospace scenario");
  }

  @Test
  void rejectsANegativePreference() {
    assertThatThrownBy(() -> habitats("1,4,2,-1\n"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must not be negative");
  }

  @Test
  void rejectsAPreferenceGivenTwice() {
    assertThatThrownBy(() -> habitats("1,4,2,1\n1,4,2,0.5\n")).isInstanceOf(
      IllegalArgumentException.class
    );
  }

  @Test
  void rejectsAHabitatMapThatDoesNotMatchTheBaseMap() {
    assertThatThrownBy(() ->
      habitats("1,1,All,0,\n1,2,Sand,1,\"1,0.5,1;0.5,1,1;\"\n", "")
    ).isInstanceOf(IllegalArgumentException.class);
  }
}
