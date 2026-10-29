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

import static eu.project.surimi.poseidon.ewe.EcopathSpeciesMapping.fromGroups;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathGroupsFromTable;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathSpeciesMappingFromGroups;
import static eu.project.surimi.poseidon.ewe.Factories.speciesFromEcopathSpeciesMapping;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import com.google.common.collect.ImmutableTable;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.biology.species.Species;

class EcopathSpeciesMappingTest {

  private static final EcopathGroup COD = new EcopathGroup(3, "Cod");
  private static final EcopathGroup JUVENILE_HAKE = new EcopathGroup(
    1,
    "Juvenile hake"
  );
  private static final EcopathGroup ADULT_HAKE = new EcopathGroup(
    2,
    "Adult hake"
  );

  private static final Species GADUS_MORHUA = new Species("COD", null, "Cod");
  private static final Species MERLUCCIUS_MERLUCCIUS = new Species(
    "HKE",
    null,
    "Hake"
  );
  private static final Species OTHER = new Species("OTHER", null, "Other");

  @Test
  void keepsTheOrderOfGroupsAndSpecies() {
    final EcopathSpeciesMapping mapping = new EcopathSpeciesMapping(
      ImmutableTable.<EcopathGroup, Species, Double>builder()
        .put(COD, GADUS_MORHUA, 0.75)
        .put(COD, OTHER, 0.25)
        .put(JUVENILE_HAKE, MERLUCCIUS_MERLUCCIUS, 1.0)
        .put(ADULT_HAKE, MERLUCCIUS_MERLUCCIUS, 1.0)
        .build()
    );
    assertThat(mapping.getGroups()).containsExactly(
      COD,
      JUVENILE_HAKE,
      ADULT_HAKE
    );
    assertThat(mapping.getSpecies()).containsExactly(
      GADUS_MORHUA,
      OTHER,
      MERLUCCIUS_MERLUCCIUS
    );
    assertThat(mapping.getShares(COD)).containsExactly(
      entry(GADUS_MORHUA, 0.75),
      entry(OTHER, 0.25)
    );
  }

  @Test
  void toleratesRoundingInTheSumOfShares() {
    final EcopathSpeciesMapping mapping = new EcopathSpeciesMapping(
      ImmutableTable.<EcopathGroup, Species, Double>builder()
        .put(COD, GADUS_MORHUA, 0.1)
        .put(COD, MERLUCCIUS_MERLUCCIUS, 0.2)
        .put(COD, OTHER, 0.7)
        .build()
    );
    assertThat(mapping.getSpecies()).hasSize(3);
  }

  @Test
  void rejectsAnEmptyMapping() {
    assertThatThrownBy(() -> new EcopathSpeciesMapping(ImmutableTable.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("has no groups");
  }

  @Test
  void rejectsAShareThatIsNotPositive() {
    assertThatThrownBy(() ->
      new EcopathSpeciesMapping(
        ImmutableTable.<EcopathGroup, Species, Double>builder()
          .put(COD, GADUS_MORHUA, 1.0)
          .put(COD, OTHER, 0.0)
          .build()
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be in (0, 1]");
  }

  @Test
  void rejectsAShareAboveOne() {
    assertThatThrownBy(() ->
      new EcopathSpeciesMapping(
        ImmutableTable.<EcopathGroup, Species, Double>builder()
          .put(COD, GADUS_MORHUA, 1.5)
          .put(COD, OTHER, -0.5)
          .build()
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be in (0, 1]");
  }

  @Test
  void rejectsANanShare() {
    assertThatThrownBy(() ->
      new EcopathSpeciesMapping(
        ImmutableTable.of(COD, GADUS_MORHUA, Double.NaN)
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be in (0, 1]");
  }

  @Test
  void rejectsSharesThatDoNotSumToOne() {
    assertThatThrownBy(() ->
      new EcopathSpeciesMapping(
        ImmutableTable.<EcopathGroup, Species, Double>builder()
          .put(COD, GADUS_MORHUA, 0.5)
          .put(COD, OTHER, 0.4)
          .build()
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must sum to 1");
  }

  @Test
  void rejectsSharesThatAreOffByMoreThanTheTolerance() {
    assertThatThrownBy(() ->
      new EcopathSpeciesMapping(ImmutableTable.of(COD, GADUS_MORHUA, 1 - 1e-8))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must sum to 1");
  }

  @Test
  void rejectsSharesOfAGroupNotInTheMapping() {
    final EcopathSpeciesMapping mapping = new EcopathSpeciesMapping(
      ImmutableTable.of(COD, GADUS_MORHUA, 1.0)
    );
    assertThatThrownBy(() -> mapping.getShares(ADULT_HAKE))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not in the species mapping");
  }

  @Test
  void mapsEachGroupToASpeciesOfItsOwn() {
    final EcopathSpeciesMapping mapping = fromGroups(
      List.of(COD, JUVENILE_HAKE)
    );
    final Species cod = new Species("3", null, "Cod");
    final Species juvenileHake = new Species("1", null, "Juvenile hake");
    assertThat(mapping.getSpecies())
      .containsExactly(cod, juvenileHake)
      .extracting(Species::getName, Species::getLifeStage)
      .containsExactly(tuple("Cod", null), tuple("Juvenile hake", null));
    assertThat(mapping.getShares(COD)).containsExactly(entry(cod, 1.0));
  }

  @Test
  void rejectsNoGroups() {
    assertThatThrownBy(() -> fromGroups(List.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("has no groups");
  }

  @Test
  void rejectsGroupsWithTheSameId() {
    assertThatThrownBy(() ->
      fromGroups(List.of(COD, new EcopathGroup(3, "Whiting")))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("More than one group with the same ID");
  }

  @Test
  void theFactoriesGiveASpeciesPerGroup() {
    assertThat(
      speciesFromEcopathSpeciesMapping(
        ecopathSpeciesMappingFromGroups(
          ecopathGroupsFromTable(
            tableFromCsvString("GroupID,GroupName\n2,Cod\n1,Detritus\n")
          )
        )
      ).get(GLOBAL_SCOPE)
    ).containsExactly(
      new Species("2", null, "Cod"),
      new Species("1", null, "Detritus")
    );
  }
}
