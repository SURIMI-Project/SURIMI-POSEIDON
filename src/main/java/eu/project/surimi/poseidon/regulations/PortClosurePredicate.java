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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Year;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.NonNull;
import uk.ac.ox.poseidon.fishing.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.fishing.vessels.HasAttributes;
import uk.ac.ox.poseidon.regulations.predicates.temporal.BetweenDates;

/**
 * Forbids a fishing action at a port/gear whose closure windows cover the
 * action's date. The home port and gear codes are those the acting vessel had
 * when it took the action, read from the attributes the action recorded. A
 * port/gear pair missing from {@code portClosures} is treated as never closed,
 * matching {@link PortClosuresFromTableFactory}'s absence semantics.
 * <p>
 * {@code portClosures} gives each port/gear pair's windows season by season,
 * for the seasons the source data was built for. Within those seasons, only
 * the recorded windows apply. After them, the closure calendar is assumed to
 * repeat indefinitely: every window of the pair's last season is shifted
 * forward by one year, two years, and so on, and each shifted copy applies
 * too. A shift of at least one year never overlaps the recorded seasons, and
 * a season holding several windows (a winter closure and a spring one, say)
 * repeats all of them. No such extrapolation is done backwards: an action
 * before the earliest recorded season is simply permitted.
 * <p>
 * Each port/gear pair's date ranges are prepared once, when the predicate is
 * built.
 */
public class PortClosurePredicate
  implements BiFunction<Object, ExtendedFishingAction, Boolean>
{

  private static final PortGearClosures NO_CLOSURES = PortGearClosures.of(
    ImmutableSortedMap.of()
  );

  private final @NonNull ImmutableMap<
    String,
    ImmutableMap<String, PortGearClosures>
  > closures;
  private final @NonNull Function<? super HasAttributes, String> homePortCode;
  private final @NonNull Function<? super HasAttributes, String> gearCode;

  public PortClosurePredicate(
    @NonNull final ImmutableMap<
      String,
      ImmutableMap<
        String,
        ImmutableSortedMap<Year, ImmutableSet<PortClosureWindow>>
      >
    > portClosures,
    @NonNull final Function<? super HasAttributes, String> homePortCode,
    @NonNull final Function<? super HasAttributes, String> gearCode
  ) {
    this.closures = portClosures
      .entrySet()
      .stream()
      .collect(
        toImmutableMap(Map.Entry::getKey, portEntry ->
          portEntry
            .getValue()
            .entrySet()
            .stream()
            .collect(
              toImmutableMap(Map.Entry::getKey, gearEntry ->
                PortGearClosures.of(gearEntry.getValue())
              )
            )
        )
      );
    this.homePortCode = homePortCode;
    this.gearCode = gearCode;
  }

  @Override
  @SuppressFBWarnings(
    value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
    justification = "SpotBugs flags the getOrDefault results as possibly null, but " +
      "Guava's getOrDefault never returns null when given a non-null default."
  )
  public Boolean apply(
    final Object context,
    final ExtendedFishingAction action
  ) {
    return closures
      .getOrDefault(homePortCode.apply(action), ImmutableMap.of())
      .getOrDefault(gearCode.apply(action), NO_CLOSURES)
      .covers(action);
  }

  private static BetweenDates betweenDates(final PortClosureWindow window) {
    return new BetweenDates(window.startDate(), window.endDate());
  }

  private record PortGearClosures(
    ImmutableList<BetweenDates> dateRanges,
    ImmutableSet<PortClosureWindow> lastSeasonWindows
  ) {
    static PortGearClosures of(
      final ImmutableSortedMap<Year, ImmutableSet<PortClosureWindow>> seasons
    ) {
      return new PortGearClosures(
        seasons
          .values()
          .stream()
          .flatMap(ImmutableSet::stream)
          .map(PortClosurePredicate::betweenDates)
          .collect(toImmutableList()),
        Iterables.getLast(seasons.values(), ImmutableSet.of())
      );
    }

    boolean covers(final ExtendedFishingAction action) {
      for (final BetweenDates dateRange : dateRanges) {
        if (dateRange.apply(null, action)) {
          return true;
        }
      }
      final var actionYear = action.getStartDateTime().getYear();
      for (final PortClosureWindow window : lastSeasonWindows) {
        // a shifted window covering the action starts or ends in its year
        if (
          shiftedCovers(
            window,
            actionYear - window.startDate().getYear(),
            action
          ) ||
          shiftedCovers(window, actionYear - window.endDate().getYear(), action)
        ) {
          return true;
        }
      }
      return false;
    }

    private static boolean shiftedCovers(
      final PortClosureWindow window,
      final int years,
      final ExtendedFishingAction action
    ) {
      return (
        years >= 1 &&
        betweenDates(
          new PortClosureWindow(
            window.startDate().plusYears(years),
            window.endDate().plusYears(years)
          )
        ).apply(null, action)
      );
    }
  }
}
