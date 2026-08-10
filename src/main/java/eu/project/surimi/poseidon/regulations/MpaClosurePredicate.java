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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import uk.ac.ox.poseidon.agents.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.geography.grids.DoubleGridWrapper;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

import java.time.Month;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Forbids a fishing action inside any MPA whose fleet restrictions cover the acting vessel's
 * (gear, country) pair AND whose closed months cover the action's month. An MPA is "inside" for a
 * cell whenever its mask in {@code mpaGrids} has a positive value there. An MPA missing from
 * {@code mpaClosedMonths} or {@code mpaFleetRestrictions} is treated as never closed / restricting
 * nobody, matching {@link MpaClosedMonthsFromTableFactory} and
 * {@link MpaFleetRestrictionsFromTableFactory}'s absence semantics. A cell covered by more than
 * one MPA is forbidden if any one of them applies.
 */
@RequiredArgsConstructor
public class MpaClosurePredicate implements Predicate<ExtendedFishingAction> {

    private final @NonNull ModelGrid modelGrid;
    private final @NonNull ImmutableMap<String, DoubleGridWrapper> mpaGrids;
    private final @NonNull ImmutableMap<String, ImmutableSet<Month>> mpaClosedMonths;
    private final @NonNull ImmutableMap<String, ImmutableSet<GearCountry>> mpaFleetRestrictions;
    private final @NonNull Function<Vessel, String> countryTagExtractor;

    @Override
    public boolean test(final ExtendedFishingAction action) {
        final var cell = modelGrid.toCell(action.getStartCoordinate());
        final var gearCountry = new GearCountry(
            action.getGear().getCode(),
            countryTagExtractor.apply(action.getAgent())
        );
        final var month = action.getStartDateTime().getMonth();

        return mpaGrids
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().getValue(cell) > 0)
            .anyMatch(entry -> {
                final String mpaId = entry.getKey();
                return mpaFleetRestrictions.getOrDefault(mpaId, ImmutableSet.of()).contains(gearCountry) &&
                    mpaClosedMonths.getOrDefault(mpaId, ImmutableSet.of()).contains(month);
            });
    }

}
