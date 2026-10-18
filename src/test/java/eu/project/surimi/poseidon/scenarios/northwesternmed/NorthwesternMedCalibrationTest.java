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

package eu.project.surimi.poseidon.scenarios.northwesternmed;

import static eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedCalibration.catchabilityParameterRanges;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.utils.ObjectFactory;

class NorthwesternMedCalibrationTest {

  // An address that named no catchability would be written as a new entry,
  // which no gear reads: the calibration would vary nothing
  @Test
  void everyCalibratedAddressSetsACatchabilityTheScenarioHas() {
    final Scenario scenario = new NorthwesternMedScenario().get();
    final Set<String> addresses = catchabilityParameterRanges().keySet();
    assertThat(addresses).isNotEmpty();
    addresses.forEach(address -> {
      final String component = address.substring(0, address.indexOf('.'));
      final String key = address.substring(
        address.indexOf('(') + 1,
        address.length() - 1
      );
      assertThat(catchabilities(scenario, component)).containsKey(key);
      assertThat(
        catchabilities(scenario.with(address, 0.5), component)
      ).containsEntry(key, 0.5);
    });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Double> catchabilities(
    final Scenario scenario,
    final String component
  ) {
    return (Map<String, Double>) scenario
      .component(component, ObjectFactory.class)
      .getValue();
  }
}
