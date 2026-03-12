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

import eu.project.surimi.poseidon.server.fleet.FleetSegmentMapper;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.agents.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.agents.regulations.actions.TemporalFishingAction;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEvent;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingOutcome;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.biology.buckets.Bucket;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.scopes.SimulationScope;
import uk.ac.ox.poseidon.core.utils.NumericIntervalMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static eu.project.surimi.poseidon.regulations.Factories.totalAllowableCatchQuotas;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.ac.ox.poseidon.core.time.Factories.dateTime;
import static uk.ac.ox.poseidon.core.utils.Factories.object;

class TotalAllowableCatchQuotasFactoryTest {

    private static final FleetSegmentMapper FLEET_SEGMENT_MAPPER =
        new FleetSegmentMapper(
            "country_of_registration",
            "loa",
            new NumericIntervalMapper<>(List.of(
                new NumericIntervalMapper.Interval<>(12.0, 18.0, "VL1218"),
                new NumericIntervalMapper.Interval<>(18.0, 24.0, "VL1824")
            )),
            "Industrial",
            "POSEIDON"
        );

    @Test
    void registersRegulationAsFishingEventListener() {
        final Simulation simulation = Scenario.builder()
            .startingDateTime(dateTime(LocalDateTime.of(2026, 1, 1, 0, 0)))
            .build()
            .startNewSimulation();
        final TotalAllowableCatchQuotas tac =
            totalAllowableCatchQuotasFactory().get(new SimulationScope(simulation));
        final Species cod = new Species("COD", null, null);
        final Vessel vessel = vessel();
        final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        final Interval interval = Interval.of(
            start.toInstant(UTC),
            start.plusDays(31).toInstant(UTC)
        );

        tac.setQuota(interval, FLEET_SEGMENT_MAPPER.apply(vessel), cod, 100.0);

        final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
        when(action.getAgent()).thenReturn(vessel);
        when(action.getStartDateTime()).thenReturn(start.plusDays(1));

        simulation.getEventManager().broadcast(
            new FishingEvent(
                action,
                new FishingOutcome(Bucket.of(cod, 100.0), null)
            )
        );
        simulation.getTemporalSchedule().stepUntil(simulation, start.plusDays(2));
        tac.step(simulation);

        assertThat(tac.isPermitted(action(vessel, interval))).isFalse();
    }

    private static TemporalFishingAction action(
        final Vessel vessel,
        final Interval interval
    ) {
        return new TemporalFishingAction() {
            @Override
            public Interval getInterval() {
                return interval;
            }

            @Override
            public Vessel getAgent() {
                return vessel;
            }
        };
    }

    private static TotalAllowableCatchQuotasFactory totalAllowableCatchQuotasFactory() {
        return totalAllowableCatchQuotas(object(FLEET_SEGMENT_MAPPER));
    }

    private static Vessel vessel() {
        final Vessel vessel = mock(Vessel.class);
        final Gear gear = mock(Gear.class);
        when(vessel.getGear()).thenReturn(gear);
        when(gear.getCode()).thenReturn("OTB");
        when(vessel.getTag("country_of_registration")).thenReturn(Optional.of("ESP"));
        when(vessel.getTag("loa")).thenReturn(Optional.of(18.5));
        return vessel;
    }
}
