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
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.stringAttribute;

import eu.project.surimi.poseidon.calibration.LandingsAccumulator;
import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.io.ScenarioLoader;
import uk.ac.ox.poseidon.io.ScenarioWriter;

/**
 * Runs with the same seed must give the same results, whatever ran before
 * them in the JVM or alongside them, and must leave the scenario as they found
 * it. Each run hands out identity hash codes, so a result that
 * depended on them (iterating a HashMap keyed by vessels, say) would differ
 * between the runs below.
 */
class ScenarioReproducibilityTest {

  // Seed 1, two months, captured before the vessel register migration.
  // A sanity check: no-op register rows can change scheduling and seeded results.
  @Test
  void localNorthwesternMedKeepsLandingsNearTheBaseline() throws IOException {
    final Map<LandingsAccumulator.Key, Double> baselineLandings =
      Files.readAllLines(
        Path.of("src/test/resources/scenarios/northwestern-med-landings.csv")
      )
        .stream()
        .map(row -> row.split(","))
        .collect(
          toMap(
            columns ->
              new LandingsAccumulator.Key(
                Integer.valueOf(columns[0]),
                columns[1],
                columns[2]
              ),
            columns -> Double.valueOf(columns[3])
          )
        );
    final Map<LandingsAccumulator.Key, Double> actualLandings = landings(
      new LocalNorthwesternMedScenario().get(),
      "main_fishing_gear",
      Period.ofMonths(2)
    );

    assertThat(actualLandings.keySet()).containsExactlyInAnyOrderElementsOf(
      baselineLandings.keySet()
    );
    baselineLandings.forEach((key, baselineKg) ->
      assertThat(actualLandings.get(key))
        .as("landings for %s", key)
        .isCloseTo(baselineKg, withinPercentage(5))
    );
    assertThat(
      actualLandings.values().stream().mapToDouble(Double::doubleValue).sum()
    )
      .as("total landings")
      .isCloseTo(
        baselineLandings
          .values()
          .stream()
          .mapToDouble(Double::doubleValue)
          .sum(),
        withinPercentage(1)
      );
  }

  @Test
  void localNorthwesternMedGivesSameLandingsWithSameSeed() {
    assertSameLandingsWithSameSeed(
      new LocalNorthwesternMedScenario(),
      "main_fishing_gear",
      Period.ofMonths(2)
    );
  }

  @Test
  void localNorthwesternMedYamlGivesTheSameLandingsAsJava() {
    final Scenario scenario = new LocalNorthwesternMedScenario().get();
    final Scenario loaded = new ScenarioLoader("eu.project.surimi").load(
      new ScenarioWriter().write(scenario)
    );

    assertThat(
      landings(loaded, "main_fishing_gear", Period.ofMonths(2))
    ).isEqualTo(landings(scenario, "main_fishing_gear", Period.ofMonths(2)));
  }

  @Test
  void minimalGivesSameLandingsWithSameSeed() {
    assertSameLandingsWithSameSeed(
      new MinimalScenario(),
      "gear",
      Period.ofYears(1)
    );
  }

  // Simulations of one scenario share its factories and their global
  // products: runs built and stepped at the same time must still give what
  // each gives alone.
  @Test
  void localNorthwesternMedRunsInParallelGiveTheirSequentialLandings() {
    final Scenario scenario = new LocalNorthwesternMedScenario().get();
    final Period period = Period.ofMonths(2);
    final Map<LandingsAccumulator.Key, Double> sequentialLandings = landings(
      scenario,
      "main_fishing_gear",
      period
    );
    final List<Map<LandingsAccumulator.Key, Double>> parallelLandings =
      IntStream.range(0, 3)
        .parallel()
        .mapToObj(_ -> landings(scenario, "main_fishing_gear", period))
        .toList();
    assertThat(parallelLandings).containsOnly(sequentialLandings);
  }

  // Building and stepping a simulation must not change the scenario's
  // factories, which every simulation of the scenario shares.
  @Test
  void runningLocalNorthwesternMedLeavesTheScenarioUnchanged() {
    assertRunLeavesScenarioUnchanged(
      new LocalNorthwesternMedScenario().get(),
      "main_fishing_gear",
      Period.ofMonths(2)
    );
  }

  @Test
  void runningMinimalLeavesTheScenarioUnchanged() {
    assertRunLeavesScenarioUnchanged(
      new MinimalScenario().get(),
      "gear",
      Period.ofYears(1)
    );
  }

  private static void assertRunLeavesScenarioUnchanged(
    final Scenario scenario,
    final String gearAttribute,
    final Period period
  ) {
    // A new writer each time: one writer keeps numbering its anchors
    final String before = new ScenarioWriter().write(scenario);
    landings(scenario, gearAttribute, period);
    assertThat(new ScenarioWriter().write(scenario)).isEqualTo(before);
  }

  private static void assertSameLandingsWithSameSeed(
    final Supplier<Scenario> scenarioSupplier,
    final String gearAttribute,
    final Period period
  ) {
    final Scenario scenario = scenarioSupplier.get();
    final Map<LandingsAccumulator.Key, Double> firstLandings = landings(
      scenario,
      gearAttribute,
      period
    );
    assertThat(firstLandings).isNotEmpty();
    assertThat(landings(scenario, gearAttribute, period)).isEqualTo(
      firstLandings
    );
    assertThat(
      landings(scenarioSupplier.get(), gearAttribute, period)
    ).isEqualTo(firstLandings);
  }

  private static Map<LandingsAccumulator.Key, Double> landings(
    final Scenario scenario,
    final String gearAttribute,
    final Period period
  ) {
    final Simulation simulation = scenario
      .withComponent(
        "landingsAccumulator",
        landingsAccumulator(stringAttribute(gearAttribute))
      )
      .startNewSimulation(1);
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
