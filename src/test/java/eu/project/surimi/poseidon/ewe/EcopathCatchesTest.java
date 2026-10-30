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

import static eu.project.surimi.poseidon.ewe.EcopathCatches.fromTable;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathCatchesFromTable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;

class EcopathCatchesTest {

  private static final String HEADER =
    "GroupID,FleetID,Landing,Discards,Price\n";

  private static final EcopathGroup COD = new EcopathGroup(1, "Cod");
  private static final EcopathGroup SHRIMP = new EcopathGroup(2, "Shrimp");
  private static final EcopathGroup DETRITUS = new EcopathGroup(3, "Detritus");

  private static Table table(final String... rows) {
    return tableFromCsvString(HEADER + String.join("", rows)).get(GLOBAL_SCOPE);
  }

  @Test
  void readsTheLandingsAndDiscardsOfEachGroupAndFleet() {
    final EcopathCatches catches = fromTable(
      table("1,1,0.5,0.1,10\n", "1,2,0.25,0,4\n")
    );
    assertThat(catches.getLanding(1, 1)).isEqualTo(0.5);
    assertThat(catches.getDiscards(1, 1)).isEqualTo(0.1);
    assertThat(catches.getLanding(1, 2)).isEqualTo(0.25);
    assertThat(catches.getDiscards(1, 2)).isZero();
  }

  @Test
  void aGroupAndFleetWithoutARowHaveNoCatch() {
    final EcopathCatches catches = fromTable(table("1,1,0.5,0.1,10\n"));
    assertThat(catches.getLanding(2, 1)).isZero();
    assertThat(catches.getDiscards(1, 2)).isZero();
  }

  @Test
  void aGroupIsCaughtIfSomeFleetLandsOrDiscardsSomeOfIt() {
    final EcopathCatches catches = fromTable(
      table("1,1,0.5,0,10\n", "2,1,0,0.2,1\n", "3,1,0,0,1\n")
    );
    assertThat(catches.isCaught(COD)).isTrue();
    assertThat(catches.isCaught(SHRIMP)).isTrue();
    assertThat(catches.isCaught(DETRITUS)).isFalse();
  }

  @Test
  void aGroupWithoutRowsIsNotCaught() {
    assertThat(fromTable(table("1,1,0.5,0,10\n")).isCaught(SHRIMP)).isFalse();
  }

  @Test
  void matchesColumnNamesCaseInsensitively() {
    final Table table = tableFromCsvString(
      "groupid,FLEETID,landing,DISCARDS\n1,1,0.5,0\n"
    ).get(GLOBAL_SCOPE);
    assertThat(fromTable(table).getLanding(1, 1)).isEqualTo(0.5);
  }

  @Test
  void theFactoryReadsTheCatches() {
    assertThat(
      ecopathCatchesFromTable(tableFromCsvString(HEADER + "1,1,0.5,0,10\n"))
        .get(GLOBAL_SCOPE)
        .getLanding(1, 1)
    ).isEqualTo(0.5);
  }

  @Test
  void rejectsAMissingColumn() {
    final Table table = tableFromCsvString(
      "GroupID,FleetID,Landing\n1,1,0\n"
    ).get(GLOBAL_SCOPE);
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("has no Discards column");
  }

  @Test
  void rejectsAMissingId() {
    final Table table = table("1,,0.5,0,10\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No value in column FleetID");
  }

  @Test
  void rejectsAMissingLanding() {
    final Table table = table("1,1,0.5,0,10\n", "2,1,,0,10\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No value in column Landing");
  }

  @Test
  void rejectsANegativeDiscard() {
    final Table table = table("1,1,0.5,-0.1,10\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Discards must not be negative");
  }

  @Test
  void rejectsTwoRowsForTheSameGroupAndFleet() {
    final Table table = table("1,1,0.5,0,10\n", "1,1,0.2,0,10\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("More than one row for the same group and fleet");
  }
}
