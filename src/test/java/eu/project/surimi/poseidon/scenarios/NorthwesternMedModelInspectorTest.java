/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025, University of Oxford.
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

import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.gui.ComponentView;
import uk.ac.ox.poseidon.gui.SimulationWithUI;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the GUI's model inspector stays cheap to refresh: MASON rebuilds the text of every
 * row it shows at each refresh, so a row showing a large component's own text (e.g. the 246,240
 * scheduled price updates of the local scenario) slows the GUI to a crawl.
 */
class NorthwesternMedModelInspectorTest {

    private static final int MAX_ROW_LENGTH = 200;

    static Stream<Supplier<Scenario>> scenarios() {
        return Stream.of(new NorthwesternMedScenario(), new LocalNorthwesternMedScenario());
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void componentRowsStayShort(final Supplier<Scenario> scenario) {
        final Scenario built = scenario.get();
        final SimulationWithUI simulationWithUI =
            new SimulationWithUI(built::startNewSimulation, List.of());
        simulationWithUI.start();

        final List<ComponentView> rows =
            ((SimulationWithUI.SimulationProxy) simulationWithUI.getSimulationInspectedObject())
                .getComponents();

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row ->
            assertThat(row.toString()).hasSizeLessThan(MAX_ROW_LENGTH)
        );
        assertThat(String.valueOf(rows)).hasSizeLessThan(MAX_ROW_LENGTH * rows.size());
        simulationWithUI.state.finish();
    }
}
