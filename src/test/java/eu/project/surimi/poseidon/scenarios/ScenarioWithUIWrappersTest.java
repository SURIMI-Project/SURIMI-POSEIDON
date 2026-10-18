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

import static org.assertj.core.api.Assertions.assertThatCode;

import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenarioWithUI;
import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenarioWithUI;
import org.junit.jupiter.api.Test;

// The wrappers take the scenario's components by name and type, which only
// the scenario's own code fixes: a wrapper that looks one up wrongly fails
// when it is built, before any window opens.
class ScenarioWithUIWrappersTest {

  @Test
  void minimalWrapperFindsTheComponentsItPortrays() {
    assertThatCode(() ->
      new MinimalScenarioWithUI(new MinimalScenario().get())
    ).doesNotThrowAnyException();
  }

  @Test
  void northwesternMedWrapperFindsTheComponentsItPortrays() {
    assertThatCode(() ->
      new NorthwesternMedScenarioWithUI(
        new LocalNorthwesternMedScenario().get()
      )
    ).doesNotThrowAnyException();
  }
}
