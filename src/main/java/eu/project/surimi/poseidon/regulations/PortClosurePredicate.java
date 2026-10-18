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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
 * {@code portClosures} only covers the years the source data was built for. For
 * an action whose year falls after the latest year recorded for its port/gear,
 * the window with the latest {@code endDate} is shifted forward by the year
 * difference and re-checked — the closure calendar is assumed to repeat
 * indefinitely into the future. No such extrapolation is done backwards: an
 * action before the earliest recorded year is simply permitted.
 */
@RequiredArgsConstructor
public class PortClosurePredicate
  implements BiFunction<Object, ExtendedFishingAction, Boolean>
{

  private final @NonNull ImmutableMap<
    String,
    ImmutableMap<String, ImmutableSet<PortClosureWindow>>
  > portClosures;
  private final @NonNull Function<? super HasAttributes, String> homePortCode;
  private final @NonNull Function<? super HasAttributes, String> gearCode;

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
    final ImmutableMap<String, ImmutableSet<PortClosureWindow>> portWindows =
      portClosures.getOrDefault(homePortCode.apply(action), ImmutableMap.of());
    final ImmutableSet<PortClosureWindow> windows = portWindows.getOrDefault(
      gearCode.apply(action),
      ImmutableSet.of()
    );
    if (
      windows
        .stream()
        .anyMatch(window -> betweenDates(window).apply(null, action))
    ) {
      return true;
    }

    final var actionYear = action.getStartDateTime().getYear();
    return windows
      .stream()
      .filter(window -> actionYear > window.endDate().getYear())
      .max(Comparator.comparing(PortClosureWindow::endDate))
      .map(latest -> {
        final var yearsAhead = actionYear - latest.endDate().getYear();
        return betweenDates(
          new PortClosureWindow(
            latest.startDate().plusYears(yearsAhead),
            latest.endDate().plusYears(yearsAhead)
          )
        ).apply(null, action);
      })
      .orElse(false);
  }

  private static BetweenDates betweenDates(final PortClosureWindow window) {
    return new BetweenDates(window.startDate(), window.endDate());
  }
}
