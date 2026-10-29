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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import java.util.List;
import lombok.NonNull;
import lombok.Value;
import uk.ac.ox.poseidon.biology.species.Species;

/**
 * How the biomass of each functional group of an Ecopath model is shared
 * between POSEIDON species: a group's biomass goes to one or more species, each
 * getting a share of it. A species can get biomass from more than one group.
 * Groups and species keep the order in which they were added to the table.
 */
@Value
public class EcopathSpeciesMapping {

  /**
   * How far the shares of a group may be from summing to 1, to allow for
   * rounding.
   */
  public static final double SHARE_SUM_TOLERANCE = 1e-9;

  /**
   * The share of each group's biomass that goes to each species, with a group
   * per row and a species per column.
   */
  @NonNull
  ImmutableTable<EcopathGroup, Species, Double> shares;

  /**
   * @param shares the share of each group's biomass that goes to each species,
   *               with a group per row and a species per column
   * @throws IllegalArgumentException if there are no shares, a share is not
   *                                  in (0, 1], or the shares of a group don't
   *                                  sum to 1, give or take
   *                                  {@link #SHARE_SUM_TOLERANCE}
   */
  public EcopathSpeciesMapping(
    @NonNull final ImmutableTable<EcopathGroup, Species, Double> shares
  ) {
    checkArgument(!shares.isEmpty(), "The species mapping has no groups");
    shares
      .cellSet()
      .forEach(cell ->
        checkArgument(
          cell.getValue() > 0 && cell.getValue() <= 1,
          "The share of group %s that goes to species %s must be in (0, 1], " +
            "but is %s",
          cell.getRowKey(),
          cell.getColumnKey(),
          cell.getValue()
        )
      );
    shares.rowMap().forEach((group, groupShares) -> {
      final double sum = groupShares
        .values()
        .stream()
        .mapToDouble(Double::doubleValue)
        .sum();
      checkArgument(
        Math.abs(sum - 1) <= SHARE_SUM_TOLERANCE,
        "The shares of group %s must sum to 1, but sum to %s",
        group,
        sum
      );
    });
    this.shares = shares;
  }

  /**
   * Maps each group to a species of its own, which gets all of its biomass:
   * the species' code is the group's ID, its name is the group's name, and it
   * has no life stage.
   *
   * @param groups the groups, in the order to keep
   * @return the mapping
   * @throws IllegalArgumentException if there are no groups or two of them
   *                                  have the same ID
   */
  public static EcopathSpeciesMapping fromGroups(
    @NonNull final List<EcopathGroup> groups
  ) {
    final ImmutableTable.Builder<EcopathGroup, Species, Double> shares =
      ImmutableTable.builder();
    groups.forEach(group ->
      shares.put(
        group,
        new Species(String.valueOf(group.getId()), null, group.getName()),
        1.0
      )
    );
    final EcopathSpeciesMapping mapping = new EcopathSpeciesMapping(
      shares.build()
    );
    checkArgument(
      mapping.getSpecies().size() == groups.size(),
      "More than one group with the same ID in %s",
      groups
    );
    return mapping;
  }

  /** @return the groups, in order */
  public ImmutableSet<EcopathGroup> getGroups() {
    return shares.rowKeySet();
  }

  /** @return the species, in order */
  public ImmutableSet<Species> getSpecies() {
    return shares.columnKeySet();
  }

  /**
   * @param group a group of the mapping
   * @return the share of the group's biomass that goes to each species
   * @throws IllegalArgumentException if the group is not in the mapping
   */
  public ImmutableMap<Species, Double> getShares(
    @NonNull final EcopathGroup group
  ) {
    checkArgument(
      shares.containsRow(group),
      "Group %s is not in the species mapping",
      group
    );
    return shares.row(group);
  }
}
