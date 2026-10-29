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

import static eu.project.surimi.poseidon.ewe.EcopathGroups.fromTable;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathGroupsFromTable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

class EcopathGroupsTest {

  private static final String HEADER = "GroupID,GroupName,Biomass\n";

  private static Table table(final String... rows) {
    return tableFromCsvString(HEADER + String.join("", rows)).get(GLOBAL_SCOPE);
  }

  @Test
  void readsTheGroupsInRowOrder() {
    final Table table = table("2,Cod,1.5\n", "1,Detritus,\n", "3,Anchovy,2\n");
    assertThat(fromTable(table)).containsExactly(
      new EcopathGroup(2, "Cod"),
      new EcopathGroup(1, "Detritus"),
      new EcopathGroup(3, "Anchovy")
    );
  }

  @Test
  void matchesColumnNamesCaseInsensitively() {
    final Table table = tableFromCsvString("groupid,GROUPNAME\n1,Cod\n").get(
      GLOBAL_SCOPE
    );
    assertThat(fromTable(table)).containsExactly(new EcopathGroup(1, "Cod"));
  }

  @Test
  void theFactoryReadsTheGroups() {
    assertThat(
      ecopathGroupsFromTable(
        tableFromCsvString(HEADER + "1,Cod,1\n2,Anchovy,2\n")
      ).get(GLOBAL_SCOPE)
    ).containsExactly(
      new EcopathGroup(1, "Cod"),
      new EcopathGroup(2, "Anchovy")
    );
  }

  @Test
  void rejectsATableWithoutGroups() {
    final Table table = Table.create(
      "EcopathGroup",
      IntColumn.create("GroupID"),
      StringColumn.create("GroupName")
    );
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("has no groups");
  }

  @Test
  void rejectsAMissingColumn() {
    final Table table = tableFromCsvString("GroupID\n1\n").get(GLOBAL_SCOPE);
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("has no GroupName column");
  }

  @Test
  void rejectsAMissingId() {
    final Table table = table("1,Cod,1\n", ",Anchovy,2\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No value in column GroupID");
  }

  @Test
  void rejectsANonPositiveId() {
    final Table table = table("0,Cod,1\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("GroupID must be a positive whole number");
  }

  @Test
  void rejectsAFractionalId() {
    final Table table = table("1.5,Cod,1\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("GroupID must be a positive whole number");
  }

  @Test
  void rejectsAMissingName() {
    final Table table = table("1,Cod,1\n", "2,,2\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No value in column GroupName");
  }

  @Test
  void rejectsABlankName() {
    final Table table = Table.create(
      "EcopathGroup",
      IntColumn.create("GroupID", new int[] { 1 }),
      StringColumn.create("GroupName", " ")
    );
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Blank value in column GroupName");
  }

  @Test
  void rejectsADuplicateId() {
    final Table table = table("1,Cod,1\n", "2,Anchovy,2\n", "1,Whiting,3\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("More than one group with ID 1");
  }

  @Test
  void aGroupNeedsAPositiveId() {
    assertThatThrownBy(() -> new EcopathGroup(0, "Cod"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be positive");
  }

  @Test
  void aGroupNeedsANonBlankName() {
    assertThatThrownBy(() -> new EcopathGroup(1, " "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is blank");
  }
}
