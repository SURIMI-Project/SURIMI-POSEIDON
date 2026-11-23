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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.ac.ox.poseidon.fishing.social.Factories.socialNetwork;

import com.google.common.collect.ImmutableSet;
import ec.util.MersenneTwisterFast;
import eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario;
import eu.project.surimi.poseidon.scenarios.stylisedtrawl.StylisedTrawlFisheryScenario;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.ox.poseidon.core.ProductStore;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.SimulationScope;
import uk.ac.ox.poseidon.core.events.SimpleEventManager;
import uk.ac.ox.poseidon.fishing.social.CliqueDynamicsFactory;
import uk.ac.ox.poseidon.fishing.trips.FishingTripStartEvent;
import uk.ac.ox.poseidon.fishing.vessels.FishingTrip;
import uk.ac.ox.poseidon.fishing.vessels.Fleet;
import uk.ac.ox.poseidon.fishing.vessels.Vessel;
import uk.ac.ox.poseidon.geography.ports.Port;

class ScenarioCliqueGroupingTest {

  private final Simulation simulation = mock(Simulation.class);

  static Stream<Supplier<Scenario>> scenarios() {
    return Stream.of(
      new NorthwesternMedScenario(),
      new StylisedTrawlFisheryScenario()
    );
  }

  @ParameterizedTest
  @MethodSource("scenarios")
  void groupsByHomePortAndGearWithoutReadingAbsentVessels(
    final Supplier<Scenario> scenario
  ) {
    final Port homePort = mock(Port.class);
    final Port otherPort = mock(Port.class);
    final Vessel vessel = vessel(homePort, "OTB");
    final Vessel samePortAndGear = vessel(homePort, "OTB");
    final Vessel otherHomePort = vessel(otherPort, "OTB");
    final Vessel otherGear = vessel(homePort, "PS");
    final Vessel absentVessel = mock(Vessel.class);
    when(absentVessel.getHomePort()).thenReturn(Optional.empty());
    final Fleet fleet = mock(Fleet.class);
    when(fleet.getVessels()).thenReturn(
      ImmutableSet.of(
        vessel,
        samePortAndGear,
        otherHomePort,
        otherGear,
        absentVessel
      )
    );
    when(simulation.getProductStore()).thenReturn(new ProductStore());
    when(simulation.getEventManager()).thenReturn(new SimpleEventManager());
    simulation.random = new MersenneTwisterFast(1);
    final SimulationScope scope = new SimulationScope(simulation);
    final var network = socialNetwork(_ -> fleet).get(scope);
    final var dynamics = scenario
      .get()
      .component("cliqueDynamics", CliqueDynamicsFactory.class)
      .toBuilder()
      .network(_ -> network)
      .build()
      .get(scope);
    final FishingTrip trip = mock(FishingTrip.class);
    when(trip.getVessel()).thenReturn(vessel);

    dynamics.receive(new FishingTripStartEvent(trip));

    assertThat(network.getRecipients(vessel)).containsExactly(samePortAndGear);
    assertThat(network.getRecipients(samePortAndGear)).containsExactly(vessel);
    assertThat(network.getRecipients(otherHomePort)).isEmpty();
    assertThat(network.getRecipients(otherGear)).isEmpty();
    assertThat(network.getRecipients(absentVessel)).isEmpty();
  }

  private static Vessel vessel(final Port homePort, final String gearCode) {
    final Vessel vessel = mock(Vessel.class);
    when(vessel.isPresent()).thenReturn(true);
    when(vessel.getHomePort()).thenReturn(Optional.of(homePort));
    when(vessel.getAttributes()).thenReturn(
      Map.of("main_fishing_gear", gearCode)
    );
    return vessel;
  }
}
