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

import static eu.project.surimi.poseidon.calibration.Factories.landingsAccumulator;
import static org.assertj.core.api.Assertions.assertThat;

import eu.project.surimi.poseidon.calibration.LandingsAccumulator;
import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.SimulationStartOptions;
import uk.ac.ox.poseidon.io.ScenarioWriter;

/**
 * Runs with the same seed must give the same results, whatever ran before
 * them in the JVM or alongside them, and must leave the scenario as they found
 * it. Each run hands out identity hash codes, so a result that
 * depended on them (iterating a HashMap keyed by vessels, say) would differ
 * between the runs below.
 */
class ScenarioReproducibilityTest {

  @Test
  void localNorthwesternMedGivesSameLandingsWithSameSeed() {
    assertSameLandingsWithSameSeed(
      new LocalNorthwesternMedScenario(),
      Period.ofMonths(2)
    );
  }

  @Test
  void minimalGivesSameLandingsWithSameSeed() {
    assertSameLandingsWithSameSeed(new MinimalScenario(), Period.ofYears(1));
  }

  // Simulations of one scenario share its factories, and the fleet loader
  // sets per-vessel values on them while a vessel is built: runs built and
  // stepped at the same time must still give what each gives alone.
  @Test
  void localNorthwesternMedRunsInParallelGiveTheirSequentialLandings() {
    final Scenario scenario = new LocalNorthwesternMedScenario().get();
    final Period period = Period.ofMonths(2);
    final Map<LandingsAccumulator.Key, Double> sequentialLandings = landings(
      scenario,
      period
    );
    final List<Map<LandingsAccumulator.Key, Double>> parallelLandings =
      IntStream.range(0, 3)
        .parallel()
        .mapToObj(_ -> landings(scenario, period))
        .toList();
    assertThat(parallelLandings).containsOnly(sequentialLandings);
  }

  // Building and stepping a simulation must not change the scenario's
  // factories, which every simulation of the scenario shares.
  @Test
  void runningLocalNorthwesternMedLeavesTheScenarioUnchanged() {
    assertRunLeavesScenarioUnchanged(
      new LocalNorthwesternMedScenario().get(),
      Period.ofMonths(2)
    );
  }

  @Test
  void runningMinimalLeavesTheScenarioUnchanged() {
    assertRunLeavesScenarioUnchanged(
      new MinimalScenario().get(),
      Period.ofYears(1)
    );
  }

  private static void assertRunLeavesScenarioUnchanged(
    final Scenario scenario,
    final Period period
  ) {
    // A new writer each time: one writer keeps numbering its anchors
    final String before = new ScenarioWriter().write(scenario);
    landings(scenario, period);
    assertThat(new ScenarioWriter().write(scenario)).isEqualTo(before);
  }

  private static void assertSameLandingsWithSameSeed(
    final Supplier<Scenario> scenarioSupplier,
    final Period period
  ) {
    final Scenario scenario = scenarioSupplier.get();
    final Map<LandingsAccumulator.Key, Double> firstLandings = landings(
      scenario,
      period
    );
    assertThat(firstLandings).isNotEmpty();
    assertThat(landings(scenario, period)).isEqualTo(firstLandings);
    assertThat(landings(scenarioSupplier.get(), period)).isEqualTo(
      firstLandings
    );
  }

  private static Map<LandingsAccumulator.Key, Double> landings(
    final Scenario scenario,
    final Period period
  ) {
    final Simulation simulation = scenario.startNewSimulation(
      SimulationStartOptions.builder()
        .seed(1)
        .extraComponent("landingsAccumulator", landingsAccumulator())
        .build()
    );
    try {
      simulation.getTemporalSchedule().stepFor(simulation, period);
      return Map.copyOf(
        simulation.getComponent(LandingsAccumulator.class).get()
      );
    } finally {
      simulation.finish();
    }
  }
}
