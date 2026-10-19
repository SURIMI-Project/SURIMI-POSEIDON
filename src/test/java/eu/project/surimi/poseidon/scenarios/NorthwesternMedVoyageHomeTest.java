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

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ox.poseidon.core.schedule.Factories.scheduledByDateTime;
import static uk.ac.ox.poseidon.fishing.vessels.Factories.vesselRegister;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import sim.util.Int2D;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.events.AbstractListener;
import uk.ac.ox.poseidon.fishing.trips.TripStartEvent;
import uk.ac.ox.poseidon.fishing.vessels.Fleet;
import uk.ac.ox.poseidon.fishing.vessels.Vessel;
import uk.ac.ox.poseidon.geography.ports.PortGrid;

/**
 * A purse seiner of Tarragona is registered in Alicante while the simulation
 * runs, then in Castellón the next day, while it is still travelling to
 * Alicante, about twenty hours away: from then on it departs from Castellón,
 * never from Alicante, since a voyage home is a run of its behaviour of its own
 * and the change takes effect when the voyage ends.
 */
class NorthwesternMedVoyageHomeTest {

  private static final String VESSEL_ID = "ESP000000379";
  private static final LocalDateTime FIRST_CHANGE_DATE = LocalDateTime.of(
    2013,
    1,
    10,
    0,
    0
  );
  private static final LocalDateTime SECOND_CHANGE_DATE =
    FIRST_CHANGE_DATE.plusDays(1);

  @Test
  void aHomePortChangedDuringAVoyageHomeTakesEffectOnArrival() {
    final Scenario base = new LocalNorthwesternMedScenario().get();
    final Scenario scenario = base.withComponent(
      "homePortChanges",
      scheduledByDateTime(
        vesselRegister(
          tableFromCsvString(
            """
            cfr,event,event_start_date,place_of_registration
            ESP000000379,MOD,2013-01-10,ESALC
            ESP000000379,MOD,2013-01-11,ESCAS
            """
          ),
          base.component("vesselSpecification")
        )
      )
    );
    final Simulation simulation = scenario.startNewSimulation(1);
    final List<Int2D> departureCells = new ArrayList<>();
    simulation.getEventManager().addListener(
      new AbstractListener<>(TripStartEvent.class) {
        @Override
        public void receive(final TripStartEvent event) {
          final Vessel vessel = event.getTrip().getVessel();
          final LocalDateTime now = simulation
            .getTemporalSchedule()
            .getDateTime();
          if (
            vessel.getId().equals(VESSEL_ID) && !now.isBefore(FIRST_CHANGE_DATE)
          ) {
            departureCells.add(vessel.getCell());
          }
        }
      }
    );
    try {
      final PortGrid portGrid = simulation.getComponent(PortGrid.class);
      // The second change must find the vessel at sea, on its way to Alicante
      simulation
        .getTemporalSchedule()
        .stepUntil(simulation, SECOND_CHANGE_DATE);
      final Vessel vessel = simulation
        .getComponent(Fleet.class)
        .getVessel(VESSEL_ID)
        .orElseThrow();
      assertThat(portGrid.getPorts().map(portGrid::getLocation)).doesNotContain(
        vessel.getCell()
      );
      simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
      final Int2D castellon = portGrid.getLocation(
        portGrid.getObject("ESCAS").orElseThrow()
      );
      assertThat(departureCells).isNotEmpty().containsOnly(castellon);
    } finally {
      simulation.finish();
    }
  }
}
