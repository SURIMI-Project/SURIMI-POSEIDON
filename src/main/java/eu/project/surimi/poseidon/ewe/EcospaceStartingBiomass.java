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

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.IntStream;
import lombok.NonNull;
import uk.ac.ox.poseidon.geography.areas.CellAreaCalculator;
import uk.ac.ox.poseidon.geography.grids.Cell;

/**
 * The biomass of Ecopath groups in each water cell of an Ecospace scenario at
 * the start of a run, spread as Ecospace spreads it
 * ({@code cEcoSpace.InitSpatialEquilibrium}): a cell's density is
 * {@code B × nWaterCells × HabCap / TotHabCap}, where {@code B} is the group's
 * Ecopath biomass, {@code HabCap} its normalised habitat capacity in the cell
 * (see {@link EcospaceHabitatCapacity}) and {@code TotHabCap} the sum of the
 * capacity over the water cells. The mean density over the water cells is
 * therefore {@code B}.
 * <p>
 * Densities are in tonnes per km², and become kilograms per cell through the
 * area of each cell as POSEIDON measures it, which differs slightly from
 * EwE's: EwE's cells are rectangles whose width shrinks with the cosine of the
 * latitude.
 */
public final class EcospaceStartingBiomass {

  private static final double KG_PER_TONNE = 1000;

  private EcospaceStartingBiomass() {}

  /**
   * @param ecospaceBasemap    the base map of the Ecospace scenario
   * @param ecospaceGroups     the scenario's groups, by Ecopath group ID
   * @param ecospaceHabitats   the scenario's habitats and the groups'
   *                           preferences
   * @param ecopathBiomasses   the biomass entered for the Ecopath groups
   * @param cellAreaCalculator POSEIDON's areas of the cells of the model grid,
   *                           EwE's cell {@code (row, col)} being cell
   *                           {@code (x = col, y = row)}
   * @param ecopathGroupIds    the IDs of the Ecopath groups to spread
   * @return the biomass of each group in each cell, in kg, indexed
   *         {@code [row][col]}, {@link Double#NaN} outside the water cells,
   *         by Ecopath group ID
   * @throws IllegalArgumentException if a group is not in the Ecospace
   *                                  scenario or has no Ecopath biomass
   *                                  entered, or its capacity can't be worked
   *                                  out (see {@link EcospaceHabitatCapacity})
   */
  public static ImmutableMap<Integer, double[][]> kilogramsPerCell(
    @NonNull final EcospaceBasemap ecospaceBasemap,
    @NonNull final ImmutableMap<Integer, EcospaceGroup> ecospaceGroups,
    @NonNull final EcospaceHabitats ecospaceHabitats,
    @NonNull final EcopathBiomasses ecopathBiomasses,
    @NonNull final CellAreaCalculator cellAreaCalculator,
    @NonNull final Collection<Integer> ecopathGroupIds
  ) {
    return ecopathGroupIds
      .stream()
      .distinct()
      .collect(
        ImmutableMap.toImmutableMap(
          groupId -> groupId,
          groupId ->
            kilogramsPerCell(
              ecospaceBasemap,
              ecospaceGroup(ecospaceGroups, groupId),
              ecospaceHabitats,
              ecopathBiomasses.getBiomass(groupId),
              cellAreaCalculator
            )
        )
      );
  }

  private static EcospaceGroup ecospaceGroup(
    final ImmutableMap<Integer, EcospaceGroup> ecospaceGroups,
    final int ecopathGroupId
  ) {
    checkArgument(
      ecospaceGroups.containsKey(ecopathGroupId),
      "Ecopath group %s is not in the Ecospace scenario",
      ecopathGroupId
    );
    return ecospaceGroups.get(ecopathGroupId);
  }

  private static double[][] kilogramsPerCell(
    final EcospaceBasemap ecospaceBasemap,
    final EcospaceGroup ecospaceGroup,
    final EcospaceHabitats ecospaceHabitats,
    final double biomassInTonnesPerSquareKm,
    final CellAreaCalculator cellAreaCalculator
  ) {
    final double[][] capacity = EcospaceHabitatCapacity.normalisedCapacity(
      ecospaceBasemap,
      ecospaceGroup,
      ecospaceHabitats
    );
    final double[] waterCapacities = IntStream.range(
      0,
      ecospaceBasemap.getRows()
    )
      .boxed()
      .flatMapToDouble(row ->
        IntStream.range(0, ecospaceBasemap.getCols())
          .filter(col -> ecospaceBasemap.isWater(row, col))
          .mapToDouble(col -> capacity[row][col])
      )
      .toArray();
    final double totalCapacity = Arrays.stream(waterCapacities).sum();
    final double densityPerCapacity =
      (biomassInTonnesPerSquareKm * waterCapacities.length) / totalCapacity;
    return IntStream.range(0, ecospaceBasemap.getRows())
      .mapToObj(row ->
        IntStream.range(0, ecospaceBasemap.getCols())
          .mapToDouble(col ->
            ecospaceBasemap.isWater(row, col)
              ? densityPerCapacity *
                capacity[row][col] *
                cellAreaCalculator.areaInSquareKm(new Cell(col, row)) *
                KG_PER_TONNE
              : Double.NaN
          )
          .toArray()
      )
      .toArray(double[][]::new);
  }
}
