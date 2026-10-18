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

import static eu.project.surimi.poseidon.regulations.Factories.totalAllowableCatchQuotas;
import static eu.project.surimi.poseidon.server.fleet.Factories.fleetSegmentMapper;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.core.functions.Factories.intervalLabel;
import static uk.ac.ox.poseidon.core.functions.IntervalLabelFactory.interval;
import static uk.ac.ox.poseidon.core.time.Factories.dateTime;
import static uk.ac.ox.poseidon.core.utils.Factories.object;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.doubleAttribute;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.stringAttribute;

import eu.project.surimi.poseidon.server.fleet.FleetSegmentMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.biology.buckets.Bucket;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.SimulationScope;
import uk.ac.ox.poseidon.fishing.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.fishing.regulations.actions.TemporalFishingAction;
import uk.ac.ox.poseidon.fishing.tasks.fishing.FishingEvent;
import uk.ac.ox.poseidon.fishing.tasks.fishing.FishingOutcome;

class TotalAllowableCatchQuotasFactoryTest {

  private static final FleetSegmentMapper FLEET_SEGMENT_MAPPER =
    fleetSegmentMapper(
      stringAttribute("main_fishing_gear"),
      stringAttribute("country_of_registration"),
      intervalLabel(
        doubleAttribute("loa"),
        interval(12.0, 18.0, "VL1218"),
        interval(18.0, 24.0, "VL1824")
      ),
      "Industrial"
    ).get(GLOBAL_SCOPE);

  // The attributes a vessel had when it fished
  private static final Map<String, Object> VESSEL = Map.of(
    "main_fishing_gear",
    "OTB",
    "country_of_registration",
    "ESP",
    "loa",
    18.5
  );

  @Test
  void registersRegulationAsFishingEventListener() {
    final Simulation simulation = Scenario.builder()
      .startingDateTime(dateTime(LocalDateTime.of(2026, 1, 1, 0, 0)))
      .build()
      .startNewSimulation(0);
    final TotalAllowableCatchQuotas tac =
      totalAllowableCatchQuotasFactory().get(new SimulationScope(simulation));
    final Species cod = new Species("COD", null, null);
    final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
    final Interval interval = Interval.of(
      start.toInstant(UTC),
      start.plusDays(31).toInstant(UTC)
    );

    tac.setQuota(
      interval,
      FLEET_SEGMENT_MAPPER.apply(() -> VESSEL),
      cod,
      100.0
    );

    final ExtendedFishingAction action = mock(ExtendedFishingAction.class);
    when(action.getAttributes()).thenReturn(VESSEL);
    when(action.getStartDateTime()).thenReturn(start.plusDays(1));

    simulation
      .getEventManager()
      .broadcast(
        new FishingEvent(
          action,
          new FishingOutcome(Bucket.of(cod, 100.0), null)
        )
      );
    simulation.getTemporalSchedule().stepUntil(simulation, start.plusDays(2));
    tac.step(simulation);

    assertThat(tac.isPermitted(action(interval))).isFalse();
  }

  private static TemporalFishingAction action(final Interval interval) {
    final TemporalFishingAction action = mock(TemporalFishingAction.class);
    when(action.getInterval()).thenReturn(interval);
    when(action.getAttributes()).thenReturn(VESSEL);
    return action;
  }

  private static TotalAllowableCatchQuotasFactory totalAllowableCatchQuotasFactory() {
    return totalAllowableCatchQuotas(object(FLEET_SEGMENT_MAPPER));
  }
}
