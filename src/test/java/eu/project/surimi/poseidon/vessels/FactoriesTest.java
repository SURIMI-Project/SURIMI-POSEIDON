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

package eu.project.surimi.poseidon.vessels;

import static eu.project.surimi.poseidon.vessels.Factories.*;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import java.util.List;
import org.junit.jupiter.api.Test;

class FactoriesTest {

  @Test
  void givesTheEuFleetRegisterFormatAndOnlyTheRequestedAttributes() {
    final var register = euFleetRegister(
      tableFromCsvString("cfr\n"),
      null,
      List.of("loa", "main_fishing_gear")
    );

    assertThat(register.getVesselIdColumn()).isEqualTo("cfr");
    assertThat(register.getEventCodeColumn()).isEqualTo("event");
    assertThat(register.getEventDateColumn()).isEqualTo("event_start_date");
    assertThat(register.getHomePortCodeColumn()).isEqualTo(
      "place_of_registration"
    );
    assertThat(register.getAttributeColumns()).containsExactly(
      "loa",
      "main_fishing_gear"
    );
    assertThat(register.getCreationCodes()).containsExactly(
      "CEN",
      "CST",
      "IMP",
      "CHA"
    );
    assertThat(register.getChangeCodes()).containsExactly("MOD");
    assertThat(register.getExitCodes()).containsExactly("DES", "EXP", "RET");
    assertThat(
      euFleetRegister(
        tableFromCsvString("cfr\n"),
        null,
        List.of()
      ).getAttributeColumns()
    ).isEmpty();
  }
}
