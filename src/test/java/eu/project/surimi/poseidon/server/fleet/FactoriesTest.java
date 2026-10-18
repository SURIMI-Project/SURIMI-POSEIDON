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

package eu.project.surimi.poseidon.server.fleet;

import static eu.project.surimi.poseidon.server.fleet.Factories.fleetSegmentMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.core.functions.Factories.intervalLabel;
import static uk.ac.ox.poseidon.core.functions.IntervalLabelFactory.interval;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.doubleAttribute;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.stringAttribute;

import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Scope;

class FactoriesTest {

  @Test
  void fleetSegmentMapperBuildsFactoryWithProvidedConfiguration() {
    final var gearCode = stringAttribute("main_fishing_gear");
    final var countryCode = stringAttribute("country_of_registration");
    final var vesselLengthClass = intervalLabel(
      doubleAttribute("loa"),
      interval(12.0, 18.0, "VL1218")
    );
    final FleetSegmentMapperFactory<Scope> factory = fleetSegmentMapper(
      gearCode,
      countryCode,
      vesselLengthClass,
      "Industrial"
    );

    assertThat(factory.getGearCode()).isSameAs(gearCode);
    assertThat(factory.getCountryCode()).isSameAs(countryCode);
    assertThat(factory.getVesselLengthClass()).isSameAs(vesselLengthClass);
    assertThat(factory.getScale()).isEqualTo("Industrial");
    assertThat(factory.get(GLOBAL_SCOPE)).isNotNull();
  }
}
