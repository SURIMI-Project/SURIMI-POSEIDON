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
import sim.util.Int2D;
import uk.ac.ox.poseidon.agents.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.extractors.tags.StringTagExtractor;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.geography.Coordinate;
import uk.ac.ox.poseidon.geography.grids.DoubleGridWrapper;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MpaClosurePredicateTest {

    private static final String COUNTRY_TAG = "country_of_registration";
    private static final Coordinate COORDINATE = new Coordinate(1.0, 2.0);
    private static final Int2D CELL = new Int2D(3, 4);

    @Test
    void forbidsInsideMpaWhenGearCountryRestrictedAndMonthClosed() {
        final var predicate = predicate(
            mpaGrids("mpa_1", 1.0),
            mpaClosedMonths("mpa_1", Month.JANUARY),
            mpaFleetRestrictions("mpa_1", new GearCountry("OTB", "ESP"))
        );

        assertThat(predicate.test(action("OTB", "ESP", Month.JANUARY))).isTrue();
    }

    @Test
    void permitsInsideMpaWhenGearCountryNotRestricted() {
        final var predicate = predicate(
            mpaGrids("mpa_1", 1.0),
            mpaClosedMonths("mpa_1", Month.JANUARY),
            mpaFleetRestrictions("mpa_1", new GearCountry("OTB", "ESP"))
        );

        assertThat(predicate.test(action("PS", "ESP", Month.JANUARY))).isFalse();
    }

    @Test
    void permitsInsideMpaWhenMonthNotClosed() {
        final var predicate = predicate(
            mpaGrids("mpa_1", 1.0),
            mpaClosedMonths("mpa_1", Month.JANUARY),
            mpaFleetRestrictions("mpa_1", new GearCountry("OTB", "ESP"))
        );

        assertThat(predicate.test(action("OTB", "ESP", Month.FEBRUARY))).isFalse();
    }

    @Test
    void permitsOutsideMpa() {
        final var predicate = predicate(
            mpaGrids("mpa_1", 0.0),
            mpaClosedMonths("mpa_1", Month.JANUARY),
            mpaFleetRestrictions("mpa_1", new GearCountry("OTB", "ESP"))
        );

        assertThat(predicate.test(action("OTB", "ESP", Month.JANUARY))).isFalse();
    }

    @Test
    void permitsWhenMpaHasNoFleetRestrictionsOrClosedMonthsRecorded() {
        final var predicate = predicate(
            mpaGrids("mpa_1", 1.0),
            ImmutableMap.of(),
            ImmutableMap.of()
        );

        assertThat(predicate.test(action("OTB", "ESP", Month.JANUARY))).isFalse();
    }

    @Test
    void forbidsWhenAnyOverlappingMpaApplies() {
        final var predicate = predicate(
            ImmutableMap.of(
                "mpa_1", gridWrapper(1.0),
                "mpa_2", gridWrapper(1.0)
            ),
            ImmutableMap.of(
                "mpa_1", ImmutableSet.of(Month.MARCH),
                "mpa_2", ImmutableSet.of(Month.JANUARY)
            ),
            ImmutableMap.of(
                "mpa_1", ImmutableSet.of(new GearCountry("OTB", "ESP")),
                "mpa_2", ImmutableSet.of(new GearCountry("OTB", "ESP"))
            )
        );

        assertThat(predicate.test(action("OTB", "ESP", Month.JANUARY))).isTrue();
    }

    private static MpaClosurePredicate predicate(
        final ImmutableMap<String, DoubleGridWrapper> mpaGrids,
        final ImmutableMap<String, ImmutableSet<Month>> mpaClosedMonths,
        final ImmutableMap<String, ImmutableSet<GearCountry>> mpaFleetRestrictions
    ) {
        final ModelGrid modelGrid = mock(ModelGrid.class);
        when(modelGrid.toCell(COORDINATE)).thenReturn(CELL);
        return new MpaClosurePredicate(
            modelGrid, mpaGrids, mpaClosedMonths, mpaFleetRestrictions,
            new StringTagExtractor(COUNTRY_TAG)
        );
    }

    private static ImmutableMap<String, DoubleGridWrapper> mpaGrids(
        final String mpaId,
        final double cellValue
    ) {
        return ImmutableMap.of(mpaId, gridWrapper(cellValue));
    }

    private static DoubleGridWrapper gridWrapper(final double cellValue) {
        final DoubleGridWrapper grid = mock(DoubleGridWrapper.class);
        when(grid.getValue(CELL)).thenReturn(cellValue);
        return grid;
    }

    private static ImmutableMap<String, ImmutableSet<Month>> mpaClosedMonths(
        final String mpaId,
        final Month... closedMonths
    ) {
        return ImmutableMap.of(mpaId, ImmutableSet.copyOf(closedMonths));
    }

    private static ImmutableMap<String, ImmutableSet<GearCountry>> mpaFleetRestrictions(
        final String mpaId,
        final GearCountry... restrictions
    ) {
        return ImmutableMap.of(mpaId, ImmutableSet.copyOf(restrictions));
    }

    private static ExtendedFishingAction action(
        final String gearCode,
        final String countryCode,
        final Month month
    ) {
        final Vessel vessel = mock(Vessel.class);
        when(vessel.getTag(COUNTRY_TAG)).thenReturn(Optional.of(countryCode));

        final Gear gear = mock(Gear.class);
        when(gear.getCode()).thenReturn(gearCode);

        final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
        when(action.getStartCoordinate()).thenReturn(COORDINATE);
        when(action.getGear()).thenReturn(gear);
        when(action.getAgent()).thenReturn(vessel);
        when(action.getStartDateTime()).thenReturn(LocalDateTime.of(2024, month, 1, 0, 0));

        return action;
    }
}
