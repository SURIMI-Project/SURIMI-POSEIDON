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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import uk.ac.ox.poseidon.agents.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.regulations.predicates.temporal.BetweenDates;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Forbids a fishing action at a port/gear whose closure windows cover the action's date. A
 * port/gear pair missing from {@code portClosures} is treated as never closed, matching
 * {@link PortClosuresFromTableFactory}'s absence semantics. A vessel with no home port yet
 * (possible before it's first assigned one) is permitted, for the same reason.
 * <p>
 * {@code portClosures} only covers the years the source data was built for. For an action whose
 * year falls after the latest year recorded for its port/gear, the window with the latest
 * {@code endDate} is shifted forward by the year difference and re-checked — the closure
 * calendar is assumed to repeat indefinitely into the future. No such extrapolation is done
 * backwards: an action before the earliest recorded year is simply permitted.
 */
@RequiredArgsConstructor
public class PortClosurePredicate implements Predicate<ExtendedFishingAction> {

    private final @NonNull ImmutableMap<String, ImmutableMap<String, ImmutableSet<PortClosureWindow>>> portClosures;

    @Override
    @SuppressFBWarnings(
        value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
        justification = "SpotBugs flags action.getGear().getCode() and the getOrDefault result " +
            "as possibly null, but ExtendedFishingAction.gear is @NonNull and always set, and " +
            "Guava's getOrDefault never returns null when given a non-null default."
    )
    public boolean test(final ExtendedFishingAction action) {
        final var homePort = action.getAgent().getHomePort();
        if (homePort == null) {
            return false;
        }

        final ImmutableMap<String, ImmutableSet<PortClosureWindow>> portWindows =
            portClosures.getOrDefault(homePort.getCode(), ImmutableMap.of());
        final ImmutableSet<PortClosureWindow> windows =
            portWindows.getOrDefault(action.getGear().getCode(), ImmutableSet.of());

        if (windows.stream().anyMatch(window -> betweenDates(window).test(action))) {
            return true;
        }

        final var actionYear = action.getStartDateTime().getYear();
        return windows
            .stream()
            .filter(window -> actionYear > window.endDate().getYear())
            .max(Comparator.comparing(PortClosureWindow::endDate))
            .map(latest -> {
                final var yearsAhead = actionYear - latest.endDate().getYear();
                return betweenDates(new PortClosureWindow(
                    latest.startDate().plusYears(yearsAhead),
                    latest.endDate().plusYears(yearsAhead)
                )).test(action);
            })
            .orElse(false);
    }

    private static BetweenDates betweenDates(final PortClosureWindow window) {
        return new BetweenDates(window.startDate(), window.endDate());
    }

}
