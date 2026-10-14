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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.core.functions.NumericIntervalMapper;

class FleetSegmentMapperTest {

  private static final NumericIntervalMapper<String> VESSEL_LENGTH_CLASS_MAPPER =
    new NumericIntervalMapper<>(
      List.of(
        new NumericIntervalMapper.Interval<>(12.0, 18.0, "VL1218"),
        new NumericIntervalMapper.Interval<>(18.0, 24.0, "VL1824")
      )
    );

  @Test
  void mapsGearCountryLengthScaleAndModel() {
    final FleetSegmentMapper mapper = new FleetSegmentMapper(
      "country_of_registration",
      "loa",
      VESSEL_LENGTH_CLASS_MAPPER,
      "Industrial"
    );
    final Vessel vessel = mock(Vessel.class);
    final Gear gear = mock(Gear.class);
    when(vessel.getGear()).thenReturn(gear);
    when(gear.getCode()).thenReturn("OTB");
    when(vessel.getTag("country_of_registration")).thenReturn(
      java.util.Optional.of("ESP")
    );
    when(vessel.getTag("loa")).thenReturn(java.util.Optional.of(18.5));

    final FleetSegment fleetSegment = mapper.apply(vessel);

    assertThat(fleetSegment).isEqualTo(
      new FleetSegment("OTB", "VL1824", "Industrial", "ESP", "POSEIDON")
    );
  }

  @Test
  void returnsNullForMissingOptionalFields() {
    final FleetSegmentMapper mapper = new FleetSegmentMapper(
      "country_of_registration",
      "loa",
      VESSEL_LENGTH_CLASS_MAPPER,
      "Industrial"
    );
    final Vessel vessel = mock(Vessel.class);
    when(vessel.getTag("country_of_registration")).thenReturn(
      java.util.Optional.empty()
    );
    when(vessel.getTag("loa")).thenReturn(java.util.Optional.empty());

    final FleetSegment fleetSegment = mapper.apply(vessel);

    assertThat(fleetSegment).isEqualTo(
      new FleetSegment(null, null, "Industrial", null, "POSEIDON")
    );
  }

  @Test
  void treatsBlankNaAndNonNumericLengthTagsAsMissing() {
    final FleetSegmentMapper mapper = new FleetSegmentMapper(
      "country_of_registration",
      "loa",
      VESSEL_LENGTH_CLASS_MAPPER,
      "Industrial"
    );
    final Vessel vessel = mock(Vessel.class);
    when(vessel.getTag("country_of_registration")).thenReturn(
      java.util.Optional.of(" NA ")
    );
    when(vessel.getTag("loa")).thenReturn(
      java.util.Optional.of("not-a-number")
    );

    final FleetSegment fleetSegment = mapper.apply(vessel);

    assertThat(fleetSegment.getCountryCode()).isNull();
    assertThat(fleetSegment.getVesselLengthClass()).isNull();
  }

  @Test
  void returnsNullWhenLengthFallsOutsideConfiguredIntervals() {
    final FleetSegmentMapper mapper = new FleetSegmentMapper(
      "country_of_registration",
      "loa",
      VESSEL_LENGTH_CLASS_MAPPER,
      "Industrial"
    );
    final Vessel vessel = mock(Vessel.class);
    when(vessel.getTag("country_of_registration")).thenReturn(
      java.util.Optional.of("ESP")
    );
    when(vessel.getTag("loa")).thenReturn(java.util.Optional.of(9.0));

    final FleetSegment fleetSegment = mapper.apply(vessel);

    assertThat(fleetSegment.getCountryCode()).isEqualTo("ESP");
    assertThat(fleetSegment.getVesselLengthClass()).isNull();
  }
}
