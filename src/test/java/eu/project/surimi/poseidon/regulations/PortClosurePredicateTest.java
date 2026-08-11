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
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.agents.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.geography.ports.Port;

import java.time.Duration;
import java.time.LocalDate;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortClosurePredicateTest {

    private static final String PORT_CODE = "ESBCN";

    @Test
    void forbidsWhenPortGearAndDateMatchAWindow() {
        final var predicate = predicate(window(2024, 1, 1, 2024, 1, 31));
        assertThat(predicate.test(action(PORT_CODE, "OTB", LocalDate.of(2024, 1, 15)))).isTrue();
    }

    @Test
    void permitsWhenGearDoesNotMatch() {
        final var predicate = predicate(window(2024, 1, 1, 2024, 1, 31));
        assertThat(predicate.test(action(PORT_CODE, "PS", LocalDate.of(2024, 1, 15)))).isFalse();
    }

    @Test
    void permitsWhenDateFallsOutsideTheWindow() {
        final var predicate = predicate(window(2024, 1, 1, 2024, 1, 31));
        assertThat(predicate.test(action(PORT_CODE, "OTB", LocalDate.of(2024, 2, 1)))).isFalse();
    }

    @Test
    void permitsForAnUnlistedPort() {
        final var predicate = predicate(window(2024, 1, 1, 2024, 1, 31));
        assertThat(predicate.test(action("ESVLC", "OTB", LocalDate.of(2024, 1, 15)))).isFalse();
    }

    @Test
    void forbidsForAFutureYearByExtrapolatingTheLatestWindow() {
        final var predicate = predicate(window(2025, 1, 1, 2025, 1, 31));
        assertThat(predicate.test(action(PORT_CODE, "OTB", LocalDate.of(2028, 1, 15)))).isTrue();
    }

    @Test
    void permitsForAFutureYearWhenTheExtrapolatedWindowDoesNotCoverThatDate() {
        final var predicate = predicate(window(2025, 1, 1, 2025, 1, 31));
        assertThat(predicate.test(action(PORT_CODE, "OTB", LocalDate.of(2028, 2, 1)))).isFalse();
    }

    @Test
    void permitsWhenVesselHasNoHomePort() {
        final var predicate = predicate(window(2024, 1, 1, 2024, 1, 31));

        final Vessel vessel = mock(Vessel.class);
        when(vessel.getHomePort()).thenReturn(null);

        final Gear gear = mock(Gear.class);
        when(gear.getCode()).thenReturn("OTB");

        final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
        when(action.getAgent()).thenReturn(vessel);
        when(action.getGear()).thenReturn(gear);

        assertThat(predicate.test(action)).isFalse();
    }

    private static PortClosurePredicate predicate(final PortClosureWindow... windows) {
        return new PortClosurePredicate(
            ImmutableMap.of(
                PORT_CODE, ImmutableMap.of("OTB", ImmutableSet.copyOf(windows))
            )
        );
    }

    private static PortClosureWindow window(
        final int startYear, final int startMonth, final int startDay,
        final int endYear, final int endMonth, final int endDay
    ) {
        return new PortClosureWindow(
            LocalDate.of(startYear, startMonth, startDay),
            LocalDate.of(endYear, endMonth, endDay)
        );
    }

    private static ExtendedFishingAction action(
        final String portCode,
        final String gearCode,
        final LocalDate date
    ) {
        final Port port = mock(Port.class);
        when(port.getCode()).thenReturn(portCode);

        final Vessel vessel = mock(Vessel.class);
        when(vessel.getHomePort()).thenReturn(port);

        final Gear gear = mock(Gear.class);
        when(gear.getCode()).thenReturn(gearCode);

        final var startDateTime = date.atStartOfDay();
        final var interval = Interval.of(startDateTime.toInstant(UTC), Duration.ofHours(1));

        final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
        when(action.getAgent()).thenReturn(vessel);
        when(action.getGear()).thenReturn(gear);
        when(action.getInterval()).thenReturn(interval);
        when(action.getStartDateTime()).thenReturn(startDateTime);

        return action;
    }

}
