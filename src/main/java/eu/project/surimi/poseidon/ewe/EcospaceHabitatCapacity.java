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

import java.util.Optional;
import java.util.stream.IntStream;
import lombok.NonNull;

/**
 * Works out a group's habitat capacity in each water cell of an Ecospace
 * scenario as Ecospace does before a run ({@code cEcoSpace.SetHabCap}), for the
 * capacity sources POSEIDON supports:
 * <ol>
 * <li>The base capacity is the group's capacity input map, or 1 in every water
 * cell if the map is not positive in any of them
 * ({@code setHabCapFromCapInputMap}).</li>
 * <li>If the group uses habitats, the base is multiplied by how much the
 * group uses each cell, from the habitats it prefers
 * ({@code setHabCapFromHabitat}; see {@link EcospaceHabitats}).</li>
 * <li>The capacity is divided by its maximum over the water cells, and raised
 * to at least {@link #MIN_CAPACITY} ({@code normalizeCapacityMap}).</li>
 * </ol>
 * The other steps of {@code SetHabCap} are refused rather than skipped:
 * <ul>
 * <li>a group whose capacity includes its responses to environmental driver
 * maps;</li>
 * <li>a migratory group, whose capacity Ecospace totals over its migration
 * maps;</li>
 * <li>a water cell whose capacity ends up below
 * {@link #MIN_UNADJUSTED_CAPACITY}, which Ecospace would raise, with a decay
 * by distance to better cells when the scenario's
 * {@code UseHabCapGradientCorrections} is set
 * ({@code AdjustLowHabCapsThreaded}).</li>
 * </ul>
 */
public final class EcospaceHabitatCapacity {

  /**
   * The lowest capacity of a water cell after normalisation, EwE's
   * {@code cEcoSpace.MIN_HABCAP}.
   */
  public static final double MIN_CAPACITY = 0.000001;

  /**
   * The capacity below which Ecospace adjusts a water cell, EwE's default
   * {@code MinHabCap}, which no data source sets.
   */
  public static final double MIN_UNADJUSTED_CAPACITY = 0.01;

  private EcospaceHabitatCapacity() {}

  /**
   * @param ecospaceBasemap  the base map of the Ecospace scenario
   * @param ecospaceGroup    the group
   * @param ecospaceHabitats the scenario's habitats and the groups'
   *                         preferences
   * @return the group's normalised capacity, indexed {@code [row][col]}, in
   *         (0, 1] in water cells (see
   *         {@link EcospaceBasemap#isWater(int, int)}) and {@link Double#NaN}
   *         elsewhere
   * @throws IllegalArgumentException if the group uses environmental responses
   *                                  or migrates, its capacity is not positive
   *                                  in any water cell, or Ecospace would
   *                                  adjust the capacity of a water cell (see
   *                                  the class documentation)
   */
  public static double[][] normalisedCapacity(
    @NonNull final EcospaceBasemap ecospaceBasemap,
    @NonNull final EcospaceGroup ecospaceGroup,
    @NonNull final EcospaceHabitats ecospaceHabitats
  ) {
    final int groupId = ecospaceGroup.getEcopathGroupId();
    checkArgument(
      !ecospaceGroup.isUsingEnvironmentalResponses(),
      "The habitat capacity of Ecopath group %s uses environmental responses, " +
        "which POSEIDON doesn't support yet",
      groupId
    );
    checkArgument(
      !ecospaceGroup.isMigratory(),
      "Ecopath group %s migrates, which POSEIDON doesn't support yet",
      groupId
    );
    final int rows = ecospaceBasemap.getRows();
    final int cols = ecospaceBasemap.getCols();
    final boolean hasCapacityInput = IntStream.range(0, rows).anyMatch(row ->
      IntStream.range(0, cols).anyMatch(
        col ->
          ecospaceBasemap.isWater(row, col) &&
          ecospaceGroup.getCapacityInput(row, col) > 0
      )
    );
    final Optional<double[][]> habitatUse = ecospaceGroup.isUsingHabitats()
      ? ecospaceHabitats.habitatUse(ecospaceGroup.getEcospaceGroupId())
      : Optional.empty();
    final double[][] capacity = new double[rows][cols];
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        final double base = hasCapacityInput
          ? ecospaceGroup.getCapacityInput(row, col)
          : 1;
        final int r = row;
        final int c = col;
        capacity[row][col] = ecospaceBasemap.isWater(row, col)
          ? base * habitatUse.map(use -> use[r][c]).orElse(1.0)
          : Double.NaN;
      }
    }
    final double max = maxOverWater(ecospaceBasemap, capacity);
    checkArgument(
      max > 0,
      "The habitat capacity of Ecopath group %s is not positive in any " +
        "water cell",
      groupId
    );
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        if (ecospaceBasemap.isWater(row, col)) {
          final double normalised = Math.max(
            capacity[row][col] / max,
            MIN_CAPACITY
          );
          checkArgument(
            normalised >= MIN_UNADJUSTED_CAPACITY,
            "The habitat capacity of Ecopath group %s in row %s, column %s " +
              "is %s, below %s: Ecospace would adjust it, which POSEIDON " +
              "doesn't do yet",
            groupId,
            row,
            col,
            normalised,
            MIN_UNADJUSTED_CAPACITY
          );
          capacity[row][col] = normalised;
        }
      }
    }
    return capacity;
  }

  /** @return the maximum over the water cells, or 0 if there are none */
  private static double maxOverWater(
    final EcospaceBasemap ecospaceBasemap,
    final double[][] capacity
  ) {
    return IntStream.range(0, ecospaceBasemap.getRows())
      .boxed()
      .flatMapToDouble(row ->
        IntStream.range(0, ecospaceBasemap.getCols())
          .filter(col -> ecospaceBasemap.isWater(row, col))
          .mapToDouble(col -> capacity[row][col])
      )
      .max()
      .orElse(0);
  }
}
