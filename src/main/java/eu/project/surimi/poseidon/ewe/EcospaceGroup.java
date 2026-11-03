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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * A functional group as an Ecospace scenario sees it, as in a row of EwE's
 * {@code EcospaceScenarioGroup} table: the Ecopath group it stands for, how
 * Ecospace works out its habitat capacity, and whether it migrates.
 * <p>
 * The capacity calculation type is EwE's {@code eEcospaceCapacityCalType}, a
 * set of bit flags. With no flag set ({@code Input}, 0), a group's capacity is
 * its capacity input map alone. Flag 1 ({@code EnvResponses}) multiplies it by
 * the group's responses to the environmental driver maps, and flag 2
 * ({@code Habitat}) by its use of the habitats it prefers
 * ({@code cEcoSpace.SetHabCap}).
 */
@Getter
@ToString
public final class EcospaceGroup {

  /** EwE's {@code eEcospaceCapacityCalType.EnvResponses} flag. */
  public static final int ENVIRONMENTAL_RESPONSES_FLAG = 1;

  /** EwE's {@code eEcospaceCapacityCalType.Habitat} flag. */
  public static final int HABITAT_FLAG = 2;

  /** The group's ID in the Ecospace scenario, EwE's {@code GroupID}. */
  private final int ecospaceGroupId;

  /** The ID of the Ecopath group, EwE's {@code EcopathGroupID}. */
  private final int ecopathGroupId;

  /**
   * Whether the group's habitat capacity includes its use of the habitats it
   * prefers.
   */
  private final boolean usingHabitats;

  /**
   * Whether the group's habitat capacity includes its responses to the
   * environmental driver maps.
   */
  private final boolean usingEnvironmentalResponses;

  /** Whether the group migrates, EwE's {@code IsMigratory}. */
  private final boolean migratory;

  @Getter(lombok.AccessLevel.NONE)
  @ToString.Exclude
  private final double[][] capacityInputMap;

  /**
   * @param ecospaceGroupId             the group's ID in the Ecospace
   *                                    scenario
   * @param ecopathGroupId              the ID of the Ecopath group
   * @param capacityCalculationType     EwE's {@code CapacityCalType}: a
   *                                    combination of
   *                                    {@link #ENVIRONMENTAL_RESPONSES_FLAG}
   *                                    and {@link #HABITAT_FLAG}
   * @param migratory                   whether the group migrates
   * @param capacityInputMap            the group's capacity input map, indexed
   *                                    {@code [row][col]}; it is copied
   * @throws IllegalArgumentException if an ID is not positive, or the
   *                                  calculation type has a flag EwE doesn't
   *                                  define
   */
  public EcospaceGroup(
    final int ecospaceGroupId,
    final int ecopathGroupId,
    final int capacityCalculationType,
    final boolean migratory,
    final double @NonNull [][] capacityInputMap
  ) {
    checkArgument(
      ecospaceGroupId > 0 && ecopathGroupId > 0,
      "Group IDs must be positive, but are %s (Ecospace) and %s (Ecopath)",
      ecospaceGroupId,
      ecopathGroupId
    );
    checkArgument(
      (capacityCalculationType &
        ~(ENVIRONMENTAL_RESPONSES_FLAG | HABITAT_FLAG)) ==
        0,
      "Unknown capacity calculation type %s for Ecopath group %s",
      capacityCalculationType,
      ecopathGroupId
    );
    this.ecospaceGroupId = ecospaceGroupId;
    this.ecopathGroupId = ecopathGroupId;
    this.usingHabitats = (capacityCalculationType & HABITAT_FLAG) != 0;
    this.usingEnvironmentalResponses =
      (capacityCalculationType & ENVIRONMENTAL_RESPONSES_FLAG) != 0;
    this.migratory = migratory;
    this.capacityInputMap = Arrays.stream(capacityInputMap)
      .map(double[]::clone)
      .toArray(double[][]::new);
  }

  /**
   * @param row the row, 0 being the northernmost
   * @param col the column, 0 being the westernmost
   * @return the group's capacity input in the cell; {@link Double#NaN} where
   *         the base map's depth is not positive
   */
  public double getCapacityInput(final int row, final int col) {
    return capacityInputMap[row][col];
  }
}
