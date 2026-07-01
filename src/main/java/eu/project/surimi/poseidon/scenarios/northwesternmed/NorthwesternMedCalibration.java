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

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.ImmutableLongArray;
import eu.project.surimi.poseidon.calibration.LandingsAccumulator;
import io.jenetics.util.DoubleRange;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.calibration.CalibrationProblem;
import uk.ac.ox.poseidon.calibration.CalibrationRunner;
import uk.ac.ox.poseidon.calibration.errors.SumSquaredErrors;

import java.nio.file.Path;
import java.time.Period;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static eu.project.surimi.poseidon.calibration.Factories.landingsAccumulator;
import static eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario.INPUT_PATH;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.summarizingInt;
import static java.util.stream.Collectors.toMap;
import static uk.ac.ox.poseidon.core.utils.Utils.multiStringKey;

public class NorthwesternMedCalibration {

    private static final double MIN_CATCHABILITY = 0.0;
    private static final double MAX_CATCHABILITY = 1.0;

    private static final int POPULATION_SIZE = 10;
    private static final long GENERATIONS = 5;
    private static final int STEADY_GENERATIONS = 5;
    private static final double MUTATION_PROBABILITY = 0.20;
    private static final double RECOMBINATION_PROBABILITY = 0.35;
    private static final ImmutableLongArray SEEDS = ImmutableLongArray.of(1, 2, 3);

    static void main() {

        final var targetLandings =
            readTargetLandings(INPUT_PATH.resolve("target_landings.csv"));

        final var yearsSummary = targetLandings
            .keySet()
            .stream()
            .collect(summarizingInt(LandingsAccumulator.Key::year));

        final DoubleRange catchabilityRange = new DoubleRange(MIN_CATCHABILITY, MAX_CATCHABILITY);
        final var parameterRanges =
            Table
                .read()
                .csv(INPUT_PATH.resolve("species.csv").toFile())
                .stream()
                .map(
                    row -> multiStringKey(
                        row.getString("species_code"),
                        row.getString("life_stage")
                    )
                )
                .flatMap(speciesKey ->
                    Stream
                        .of(
                            "purseSeinerCatchabilities",
                            "bottomTrawlerCatchabilities"
                        )
                        .map(x -> "components(" + x + ").delegate.value(" + speciesKey + ")")
                )
                .collect(toImmutableMap(
                    identity(),
                    _ -> catchabilityRange
                ));

        final CalibrationProblem problem =
            new CalibrationProblem(
                new NorthwesternMedScenario().get(),
                Period.ofYears(yearsSummary.getMax() - yearsSummary.getMin() + 1),
                ImmutableMap.of("landingsAccumulator", landingsAccumulator()),
                parameterRanges,
                new SumSquaredErrors<>(
                    targetLandings,
                    sim -> sim.getComponent(LandingsAccumulator.class).get()
                ),
                SEEDS
            );

        final CalibrationRunner.Result result =
            CalibrationRunner.minimize(
                problem,
                new CalibrationRunner.Options(
                    POPULATION_SIZE,
                    GENERATIONS,
                    MUTATION_PROBABILITY,
                    RECOMBINATION_PROBABILITY,
                    STEADY_GENERATIONS,
                    1,
                    -1.0,
                    2.0
                )
            );

        System.out.println("Best parameters: " + result.parameters());
        System.out.println("Best fitness: " + result.fitness());
        System.out.println("Generations: " + result.generations());

    }

    private static Map<LandingsAccumulator.Key, Double> readTargetLandings(final Path path) {
        return Table.read().csv(path.toFile())
            .stream()
            .collect(toMap(
                row -> new LandingsAccumulator.Key(
                    row.getInt("year"),
                    row.getString("gear_code"),
                    multiStringKey(
                        row.getString("species_code"),
                        row.getString("life_stage")
                    )
                ),
                row -> (double) row.getInt("landings_kg")
            ));
    }

}
