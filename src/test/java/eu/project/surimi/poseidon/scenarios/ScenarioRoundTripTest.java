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

import eu.project.surimi.poseidon.scenarios.anchovybay.AnchovyBayScenario;
import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario;
import eu.project.surimi.poseidon.scenarios.stylisedtrawl.StylisedTrawlFisheryScenario;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.io.ScenarioLoader;
import uk.ac.ox.poseidon.io.ScenarioWriter;

class ScenarioRoundTripTest {

  static Stream<Supplier<Scenario>> scenarios() {
    return Stream.of(
      new MinimalScenario(),
      new NorthwesternMedScenario(),
      new LocalNorthwesternMedScenario(),
      new StylisedTrawlFisheryScenario(),
      new AnchovyBayScenario()
    );
  }

  @ParameterizedTest
  @MethodSource("scenarios")
  void survivesRepeatedYamlRoundTrips(final Supplier<Scenario> scenario) {
    final String yaml = new ScenarioWriter().write(scenario.get());

    final Scenario loaded = new ScenarioLoader("eu.project.surimi").load(yaml);

    // Loading normalises numeric literals, such as integer elevations to doubles.
    final String normalisedYaml = new ScenarioWriter().write(loaded);
    final Scenario reloaded = new ScenarioLoader("eu.project.surimi").load(
      normalisedYaml
    );

    assertThat(new ScenarioWriter().write(reloaded)).isEqualTo(normalisedYaml);
  }
}
