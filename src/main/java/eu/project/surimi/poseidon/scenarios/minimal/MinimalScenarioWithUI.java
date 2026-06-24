/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025-2026, University of Oxford.
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

package eu.project.surimi.poseidon.scenarios.minimal;

import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.utils.ListFactory;
import uk.ac.ox.poseidon.gui.DisplayWrapper2D;
import uk.ac.ox.poseidon.gui.ScenarioWithUI;
import uk.ac.ox.poseidon.gui.portrayals.*;

import java.util.List;

import static java.awt.Color.WHITE;

public class MinimalScenarioWithUI extends ScenarioWithUI {
    public MinimalScenarioWithUI(
        final Scenario scenario
    ) {
        // noinspection unchecked
        super(
            scenario,
            List.of(
                new DisplayWrapper2D(
                    "Ocean",
                    List.of(
                        new BathymetryFieldPortrayalFactory(
                            scenario.component("bathymetricGrid")
                        ),
                        new SpeciesBiomassFieldsPortrayalFactory(
                            scenario.component("biomassGrids", ListFactory.class),
                            scenario.component("carryingCapacityGrid", ListFactory.class),
                            false
                        ),
                        new SimpleFieldPortrayalFactory(
                            "Markets",
                            new MarketGridPortrayalFactory(
                                scenario.component("marketGrid")
                            ),
                            true
                        ),
                        new SimpleFieldPortrayalFactory(
                            "Ports",
                            new PortGridPortrayalFactory(
                                scenario.component("portGrid")
                            ),
                            true
                        ),
                        new SimpleFieldPortrayalFactory(
                            "Vessels",
                            new VesselFieldPortrayalFactory(
                                scenario.component("vesselField")
                            ),
                            true
                        ),
                        new SimpleFieldPortrayalFactory(
                            "Coordinates",
                            new CoordinatesPortrayalFactory(
                                scenario.component("modelGrid"),
                                3
                            ),
                            true
                        )
                    ),
                    600,
                    600,
                    WHITE
                )
            )
        );
    }

    static void main(final String[] args) {
        final MinimalScenarioWithUI minimalScenarioWithUI =
            new MinimalScenarioWithUI(new MinimalScenario().get());
        minimalScenarioWithUI.createController();
    }
}
