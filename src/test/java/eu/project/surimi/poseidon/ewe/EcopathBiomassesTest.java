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

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;

class EcopathBiomassesTest {

  private static final String HEADER = "GroupID,Biomass\n";

  private static EcopathBiomasses biomasses(final String rows) {
    return EcopathBiomasses.fromTable(
      tableFromCsvString(HEADER + rows).get(GLOBAL_SCOPE)
    );
  }

  @Test
  void readsTheBiomassesEntered() {
    final EcopathBiomasses biomasses = biomasses("1,10\n2,0\n3,0.5\n");
    assertThat(biomasses.getBiomass(1)).isEqualTo(10);
    assertThat(biomasses.getBiomass(2)).isZero();
    assertThat(biomasses.getBiomass(3)).isEqualTo(0.5);
  }

  @Test
  void aGroupWithAMissingOrNegativeBiomassHasNoneEntered() {
    final EcopathBiomasses biomasses = biomasses("1,10\n2,\n3,-9999\n");
    assertThat(biomasses.getBiomass(1)).isEqualTo(10);
    assertThatThrownBy(() -> biomasses.getBiomass(2))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No biomass entered for Ecopath group 2");
    assertThatThrownBy(() -> biomasses.getBiomass(3))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No biomass entered for Ecopath group 3");
  }

  @Test
  void rejectsTwoRowsWithTheSameGroupId() {
    assertThatThrownBy(() -> biomasses("1,10\n1,11\n")).isInstanceOf(
      IllegalArgumentException.class
    );
  }

  @Test
  void matchesColumnNamesCaseInsensitively() {
    final Table table = tableFromCsvString("groupid,BIOMASS\n1,4\n").get(
      GLOBAL_SCOPE
    );
    assertThat(EcopathBiomasses.fromTable(table).getBiomass(1)).isEqualTo(4);
  }
}
