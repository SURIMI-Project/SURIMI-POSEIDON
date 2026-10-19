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

package eu.project.surimi.poseidon.regulations;

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.time.Month;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.NonNull;
import sim.util.Int2D;
import uk.ac.ox.poseidon.fishing.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.fishing.vessels.HasAttributes;
import uk.ac.ox.poseidon.geography.grids.DoubleGridWrapper;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

/**
 * Forbids a fishing action inside any MPA whose fleet restrictions cover the
 * (gear, country) pair the acting vessel had when it took the action, read from
 * the attributes the action recorded, AND whose closed months cover the
 * action's month. An MPA is "inside" for a cell whenever its mask in
 * {@code mpaGrids} has a positive value there. An MPA missing from
 * {@code mpaClosedMonths} or {@code mpaFleetRestrictions} is treated as never
 * closed / restricting nobody, matching {@link MpaClosedMonthsFromTableFactory}
 * and {@link MpaFleetRestrictionsFromTableFactory}'s absence semantics. A cell
 * covered by more than one MPA is forbidden if any one of them applies.
 * <p>
 * The MPAs covering each cell are found once, when the predicate is built, so
 * the grids are read only then.
 */
public class MpaClosurePredicate
  implements BiFunction<Object, ExtendedFishingAction, Boolean>
{

  private final @NonNull ModelGrid modelGrid;
  private final @NonNull ImmutableListMultimap<Int2D, String> mpaIdsByCell;
  private final @NonNull ImmutableMap<
    String,
    ImmutableSet<Month>
  > mpaClosedMonths;
  private final @NonNull ImmutableMap<
    String,
    ImmutableSet<GearCountry>
  > mpaFleetRestrictions;
  private final @NonNull Function<? super HasAttributes, String> gearCode;
  private final @NonNull Function<? super HasAttributes, String> countryCode;

  public MpaClosurePredicate(
    @NonNull final ModelGrid modelGrid,
    @NonNull final ImmutableMap<String, DoubleGridWrapper> mpaGrids,
    @NonNull final ImmutableMap<String, ImmutableSet<Month>> mpaClosedMonths,
    @NonNull final ImmutableMap<
      String,
      ImmutableSet<GearCountry>
    > mpaFleetRestrictions,
    @NonNull final Function<? super HasAttributes, String> gearCode,
    @NonNull final Function<? super HasAttributes, String> countryCode
  ) {
    this.modelGrid = modelGrid;
    this.mpaIdsByCell = modelGrid
      .getAllCells()
      .flatMap(cell ->
        mpaGrids
          .entrySet()
          .stream()
          .filter(entry -> entry.getValue().getValue(cell) > 0)
          .map(entry -> Map.entry(cell, entry.getKey()))
      )
      .collect(toImmutableListMultimap(Map.Entry::getKey, Map.Entry::getValue));
    this.mpaClosedMonths = mpaClosedMonths;
    this.mpaFleetRestrictions = mpaFleetRestrictions;
    this.gearCode = gearCode;
    this.countryCode = countryCode;
  }

  @Override
  public Boolean apply(
    final Object context,
    final ExtendedFishingAction action
  ) {
    final var mpaIds = mpaIdsByCell.get(
      modelGrid.toCell(action.getStartCoordinate())
    );
    if (mpaIds.isEmpty()) {
      return false;
    }
    final var gearCountry = new GearCountry(
      gearCode.apply(action),
      countryCode.apply(action)
    );
    final var month = action.getStartDateTime().getMonth();
    for (final String mpaId : mpaIds) {
      final var restrictedFleets = mpaFleetRestrictions.get(mpaId);
      final var closedMonths = mpaClosedMonths.get(mpaId);
      if (
        restrictedFleets != null &&
        restrictedFleets.contains(gearCountry) &&
        closedMonths != null &&
        closedMonths.contains(month)
      ) {
        return true;
      }
    }
    return false;
  }
}
