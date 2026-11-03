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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.geography.allocators.Allocator;
import uk.ac.ox.poseidon.geography.areas.CellAreaCalculator;
import uk.ac.ox.poseidon.geography.grids.Cell;

/**
 * A {@link RelativeScopeFactory} for the starting biomass of species in an
 * Ecospace scenario, as one {@link Allocator} per species, in the order of the
 * species given, for
 * {@link uk.ac.ox.poseidon.biology.biomass.Factories#biomassGrids}. A species
 * gets, in each water cell, its share of the biomass of each Ecopath group
 * that the {@link EcopathSpeciesMapping} maps to it, spread over the cells as
 * {@link EcospaceStartingBiomass} describes, in kg; outside the water cells it
 * gets {@link Double#NaN}. EwE's cell {@code (row, col)} is the model grid's
 * cell {@code (x = col, y = row)}. Built via
 * {@link Factories Factories.biomassAllocatorsFromEcospace(...)}.
 */
@Getter
@ToString
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class BiomassAllocatorsFromEcospaceFactory<S extends Scope> extends RelativeScopeFactory<
  S,
  List<Allocator>
> {

  private final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap;
  private final Factory<
    ? super S,
    ? extends ImmutableMap<Integer, EcospaceGroup>
  > ecospaceGroups;
  private final Factory<? super S, ? extends EcospaceHabitats> ecospaceHabitats;
  private final Factory<? super S, ? extends EcopathBiomasses> ecopathBiomasses;
  private final Factory<
    ? super S,
    ? extends CellAreaCalculator
  > cellAreaCalculator;
  private final Factory<
    ? super S,
    ? extends EcopathSpeciesMapping
  > ecopathSpeciesMapping;
  private final Factory<? super S, ? extends List<? extends Species>> species;

  @Override
  protected List<Allocator> newInstance(final S scope) {
    final EcopathSpeciesMapping mapping = ecopathSpeciesMapping.get(scope);
    final ImmutableMap<Integer, double[][]> kilograms =
      EcospaceStartingBiomass.kilogramsPerCell(
        ecospaceBasemap.get(scope),
        ecospaceGroups.get(scope),
        ecospaceHabitats.get(scope),
        ecopathBiomasses.get(scope),
        cellAreaCalculator.get(scope),
        mapping
          .getShares()
          .rowKeySet()
          .stream()
          .map(EcopathGroup::getId)
          .toList()
      );
    return species
      .get(scope)
      .stream()
      .map(oneSpecies -> allocator(oneSpecies, mapping, kilograms))
      .collect(ImmutableList.toImmutableList());
  }

  private static Allocator allocator(
    final Species species,
    final EcopathSpeciesMapping mapping,
    final ImmutableMap<Integer, double[][]> kilograms
  ) {
    final Map<EcopathGroup, Double> shares = mapping
      .getShares()
      .column(species);
    checkArgument(
      !shares.isEmpty(),
      "No Ecopath group maps to species %s",
      species
    );
    final double[][] speciesKilograms = shares
      .entrySet()
      .stream()
      .map(entry -> scaled(groupKilograms(kilograms, entry.getKey()), entry))
      .reduce(BiomassAllocatorsFromEcospaceFactory::sum)
      .orElseThrow();
    return (final Cell cell) -> speciesKilograms[cell.y()][cell.x()];
  }

  private static double[][] groupKilograms(
    final ImmutableMap<Integer, double[][]> kilograms,
    final EcopathGroup group
  ) {
    return Optional.ofNullable(kilograms.get(group.getId())).orElseThrow(() ->
      new IllegalStateException("No starting biomass for group " + group)
    );
  }

  private static double[][] scaled(
    final double[][] grid,
    final Map.Entry<EcopathGroup, Double> share
  ) {
    return Arrays.stream(grid)
      .map(row ->
        Arrays.stream(row)
          .map(value -> value * share.getValue())
          .toArray()
      )
      .toArray(double[][]::new);
  }

  private static double[][] sum(final double[][] a, final double[][] b) {
    return IntStream.range(0, a.length)
      .mapToObj(row ->
        IntStream.range(0, a[row].length)
          .mapToDouble(col -> a[row][col] + b[row][col])
          .toArray()
      )
      .toArray(double[][]::new);
  }
}
