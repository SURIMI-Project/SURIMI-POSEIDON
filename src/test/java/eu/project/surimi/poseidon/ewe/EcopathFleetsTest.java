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

import static eu.project.surimi.poseidon.ewe.EcopathFleets.fromTable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

class EcopathFleetsTest {

  private static final String HEADER = "FleetID,FleetName,FixedCost\n";

  private static Table table(final String... rows) {
    return tableFromCsvString(HEADER + String.join("", rows)).get(GLOBAL_SCOPE);
  }

  @Test
  void readsTheFleetsInRowOrder() {
    final Table table = table("2,Trawlers,0\n", "1,Sealers,\n");
    assertThat(fromTable(table)).containsExactly(
      new EcopathFleet(2, "Trawlers"),
      new EcopathFleet(1, "Sealers")
    );
  }

  @Test
  void matchesColumnNamesCaseInsensitively() {
    final Table table = tableFromCsvString(
      "fleetid,FLEETNAME\n1,Seiners\n"
    ).get(GLOBAL_SCOPE);
    assertThat(fromTable(table)).containsExactly(
      new EcopathFleet(1, "Seiners")
    );
  }

  @Test
  void rejectsATableWithoutFleets() {
    final Table table = Table.create(
      "EcopathFleet",
      IntColumn.create("FleetID"),
      StringColumn.create("FleetName")
    );
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("has no fleets");
  }

  @Test
  void rejectsAMissingId() {
    final Table table = table("1,Sealers,0\n", ",Trawlers,0\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No value in column FleetID");
  }

  @Test
  void rejectsANonPositiveId() {
    final Table table = table("0,Sealers,0\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("FleetID must be a positive whole number");
  }

  @Test
  void rejectsAMissingName() {
    final Table table = table("1,Sealers,0\n", "2,,0\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No value in column FleetName");
  }

  @Test
  void rejectsADuplicateId() {
    final Table table = table("1,Sealers,0\n", "1,Trawlers,0\n");
    assertThatThrownBy(() -> fromTable(table))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("More than one fleet with ID 1");
  }

  @Test
  void aFleetNeedsAPositiveId() {
    assertThatThrownBy(() -> new EcopathFleet(0, "Sealers"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be positive");
  }

  @Test
  void aFleetNeedsANonBlankName() {
    assertThatThrownBy(() -> new EcopathFleet(1, " "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is blank");
  }
}
