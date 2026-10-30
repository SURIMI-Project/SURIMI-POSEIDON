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

import static eu.project.surimi.poseidon.ewe.Factories.caughtInEcopath;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathCatchesFromTable;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathGroupProperty;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathGroupsFromTable;
import static eu.project.surimi.poseidon.ewe.Factories.filteredEcopathGroups;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.core.predicates.Factories.condition;
import static uk.ac.ox.poseidon.core.predicates.Factories.in;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.anyOf;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.not;
import static uk.ac.ox.poseidon.core.utils.Factories.setOf;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Scope;

class FilteredEcopathGroupsTest {

  private static final EcopathGroupsFromTableFactory<Scope> GROUPS =
    ecopathGroupsFromTable(
      tableFromCsvString(
        "GroupID,GroupName\n3,Shrimp\n1,Detritus\n2,Cod\n4,Whales\n"
      )
    );

  // Cod is landed and Shrimp only discarded; Whales has a row with no catch,
  // and Detritus no row at all
  private static final CaughtInEcopathFactory<Scope> CAUGHT = caughtInEcopath(
    ecopathCatchesFromTable(
      tableFromCsvString(
        "GroupID,FleetID,Landing,Discards\n2,1,0.5,0\n3,1,0,0.1\n4,1,0,0\n"
      )
    )
  );

  @Test
  void keepsTheGroupsCaughtInEcopathInTheirOrder() {
    assertThat(filteredEcopathGroups(GROUPS, CAUGHT).get(GLOBAL_SCOPE))
      .extracting(EcopathGroup::getName)
      .containsExactly("Shrimp", "Cod");
  }

  @Test
  void takesAnyPredicate() {
    assertThat(filteredEcopathGroups(GROUPS, not(CAUGHT)).get(GLOBAL_SCOPE))
      .extracting(EcopathGroup::getName)
      .containsExactly("Detritus", "Whales");
  }

  @Test
  void selectsGroupsByName() {
    assertThat(
      filteredEcopathGroups(
        GROUPS,
        condition(ecopathGroupProperty("name"), in(setOf("Cod", "Whales")))
      ).get(GLOBAL_SCOPE)
    )
      .extracting(EcopathGroup::getName)
      .containsExactly("Cod", "Whales");
  }

  @Test
  void selectsGroupsById() {
    assertThat(
      filteredEcopathGroups(
        GROUPS,
        condition(ecopathGroupProperty("id"), in(setOf(1, 3)))
      ).get(GLOBAL_SCOPE)
    )
      .extracting(EcopathGroup::getName)
      .containsExactly("Shrimp", "Detritus");
  }

  @Test
  void combinesTheRuleWithAList() {
    assertThat(
      filteredEcopathGroups(
        GROUPS,
        anyOf(
          CAUGHT,
          condition(ecopathGroupProperty("name"), in(setOf("Whales")))
        )
      ).get(GLOBAL_SCOPE)
    )
      .extracting(EcopathGroup::getName)
      .containsExactly("Shrimp", "Cod", "Whales");
  }

  @Test
  void failsIfNoGroupPasses() {
    final var groups = ecopathGroupsFromTable(
      tableFromCsvString("GroupID,GroupName\n1,Detritus\n")
    );
    assertThatThrownBy(() ->
      filteredEcopathGroups(groups, CAUGHT).get(GLOBAL_SCOPE)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("None of the groups");
  }
}
