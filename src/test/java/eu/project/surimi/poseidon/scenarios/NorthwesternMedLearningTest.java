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

package eu.project.surimi.poseidon.scenarios;

import static eu.project.surimi.poseidon.vessels.Factories.*;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ox.poseidon.core.schedule.Factories.scheduledByDateTime;
import static uk.ac.ox.poseidon.fishing.vessels.Factories.vesselRegister;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.events.AbstractListener;
import uk.ac.ox.poseidon.fishing.tasks.fishing.FishingActionEvent;
import uk.ac.ox.poseidon.fishing.trips.FishingTripEndEvent;
import uk.ac.ox.poseidon.fishing.trips.FishingTripStartEvent;
import uk.ac.ox.poseidon.fishing.vessels.Vessel;

/**
 * What the scenario's learning assembly does in a run: both gears choose their
 * destination at departure and again at sea, a gear the scenario does not
 * support leaves a vessel without behaviour, and a gear change from the dated
 * register changes what the vessel does.
 */
class NorthwesternMedLearningTest {

  private static final String PURSE_SEINER_ID = "ESP000000379";
  private static final String GEAR = "main_fishing_gear";
  private static final LocalDateTime CHANGE_DATE = LocalDateTime.of(
    2013,
    1,
    10,
    0,
    0
  );

  private static Scenario withGearChange(final String gear) {
    final Scenario base = new LocalNorthwesternMedScenario().get();
    return base.withComponent(
      "gearChange",
      scheduledByDateTime(
        vesselRegister(
          tableFromCsvString(
            "cfr,event,event_start_date,place_of_registration,main_fishing_gear\n" +
              PURSE_SEINER_ID +
              ",MOD,2013-01-10,," +
              gear +
              "\n"
          ),
          base.component("vesselSpecification"),
          euFleetRegisterColumns(List.of("main_fishing_gear")),
          euFleetRegisterEventCodes()
        )
      )
    );
  }

  @Test
  void bothGearsFishMoreThanOnceAtSeaAfterTheirDeparturePick() {
    final Simulation simulation = new LocalNorthwesternMedScenario()
      .get()
      .startNewSimulation(1);
    final Map<Vessel, Integer> eventsInTrip = new HashMap<>();
    final Map<Object, Integer> mostEventsInATripByGear = new HashMap<>();
    simulation.getEventManager().addListener(
      new AbstractListener<>(FishingActionEvent.class) {
        @Override
        public void receive(final FishingActionEvent event) {
          eventsInTrip.merge(event.getVessel(), 1, Integer::sum);
        }
      }
    );
    simulation.getEventManager().addListener(
      new AbstractListener<>(FishingTripEndEvent.class) {
        @Override
        public void receive(final FishingTripEndEvent event) {
          final Vessel vessel = event.getVessel();
          final Integer events = eventsInTrip.remove(vessel);
          if (events != null) {
            mostEventsInATripByGear.merge(
              vessel.getAttributes().get(GEAR),
              events,
              Math::max
            );
          }
        }
      }
    );
    try {
      simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
    } finally {
      simulation.finish();
    }
    assertThat(mostEventsInATripByGear).containsKeys("PS", "OTB");
    assertThat(mostEventsInATripByGear.values()).allMatch(n -> n >= 2);
  }

  @Test
  void aVesselRegisteredWithAnUnsupportedGearStopsFishing() {
    final Simulation simulation = withGearChange("XYZ").startNewSimulation(1);
    final Set<LocalDateTime> tripStartsAfterTheChange = new HashSet<>();
    final Set<LocalDateTime> tripStartsBeforeTheChange = new HashSet<>();
    simulation.getEventManager().addListener(
      new AbstractListener<>(FishingTripStartEvent.class) {
        @Override
        public void receive(final FishingTripStartEvent event) {
          if (event.getVessel().getId().equals(PURSE_SEINER_ID)) {
            final LocalDateTime now = simulation
              .getTemporalSchedule()
              .getDateTime();
            (now.isBefore(CHANGE_DATE)
              ? tripStartsBeforeTheChange
              : tripStartsAfterTheChange
            ).add(now);
          }
        }
      }
    );
    try {
      simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
    } finally {
      simulation.finish();
    }
    assertThat(tripStartsBeforeTheChange).isNotEmpty();
    assertThat(tripStartsAfterTheChange).isEmpty();
  }

  // A trip under way at the change finishes with the old gear, so the
  // events are compared from a few days after it.
  @Test
  void aGearChangeFromTheRegisterChangesTheGearOfTheVesselsFishingEvents() {
    final Simulation simulation = withGearChange("OTB").startNewSimulation(1);
    final Set<Object> gearsBeforeTheChange = new HashSet<>();
    final Set<Object> gearsAfterTheChange = new HashSet<>();
    simulation.getEventManager().addListener(
      new AbstractListener<>(FishingActionEvent.class) {
        @Override
        public void receive(final FishingActionEvent event) {
          final Vessel vessel = event.getVessel();
          if (vessel.getId().equals(PURSE_SEINER_ID)) {
            final LocalDateTime now = simulation
              .getTemporalSchedule()
              .getDateTime();
            if (now.isBefore(CHANGE_DATE)) {
              gearsBeforeTheChange.add(vessel.getAttributes().get(GEAR));
            } else if (!now.isBefore(CHANGE_DATE.plusDays(3))) {
              gearsAfterTheChange.add(vessel.getAttributes().get(GEAR));
            }
          }
        }
      }
    );
    try {
      simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
    } finally {
      simulation.finish();
    }
    assertThat(gearsBeforeTheChange).containsExactly("PS");
    assertThat(gearsAfterTheChange).containsExactly("OTB");
  }
}
