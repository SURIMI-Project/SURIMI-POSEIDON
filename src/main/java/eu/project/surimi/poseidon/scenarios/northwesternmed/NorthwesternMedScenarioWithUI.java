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

package eu.project.surimi.poseidon.scenarios.northwesternmed;

import uk.ac.ox.poseidon.biology.biomass.BiomassGrid;
import uk.ac.ox.poseidon.biology.biomass.CarryingCapacityGridFactory;
import uk.ac.ox.poseidon.biology.biomass.Factories;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.scopes.SimulationScope;
import uk.ac.ox.poseidon.gui.DisplayWrapper2D;
import uk.ac.ox.poseidon.gui.ScenarioWithUI;
import uk.ac.ox.poseidon.gui.portrayals.*;

import java.util.List;

import static java.awt.Color.WHITE;
import static uk.ac.ox.poseidon.core.quantities.Factories.massOf;
import static uk.ac.ox.poseidon.core.utils.Factories.listOf;

public class NorthwesternMedScenarioWithUI extends ScenarioWithUI {

    private static final int WIDTH = 1090;
    private static final int HEIGHT = 820;

    // purely a color-scale ceiling for the per-species biomass panel below;
    // decoupled from the scenario's own (now zero-initialized) biomass state.
    // 700 t comfortably covers the largest single-cell biomass value seen across every
    // species and every yearly snapshot in inputs/northwestern_med/biomass_grids.nc
    // (BOY tops out at ~673.9 t), so the scale doesn't clip the most abundant stocks.
    private static final String DISPLAY_CARRYING_CAPACITY = "700000 kg";

    @SuppressWarnings({"unchecked", "rawtypes"})
    public NorthwesternMedScenarioWithUI(final Scenario scenario) {
        super(
            scenario,
            List.of(
                new DisplayWrapper2D(
                    "Catalan Mediterranean Sea",
                    List.of(
                        new BathymetryFieldPortrayalFactory(
                            scenario.component("bathymetricGrid")
                        ),
                        new SpeciesBiomassFieldsPortrayalFactory(
                            (Factory<? super SimulationScope, List<? extends BiomassGrid>>)
                                scenario.component("biomassGrids"),
                            listOf(
                                (CarryingCapacityGridFactory) Factories.<SimulationScope>uniformCarryingCapacityGrid(
                                    scenario.component("modelGrid"),
                                    scenario.component("bathymetricGrid"),
                                    massOf(DISPLAY_CARRYING_CAPACITY)
                                )
                            ),
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
                            "Purse seiner regulations",
                            new RegulationGridPortrayalFactory(
                                scenario.component("purseSeinerRegulations"),
                                scenario.component("vesselField"),
                                scenario.component("bathymetricGrid"),
                                WIDTH,
                                HEIGHT
                            ),
                            true
                        ),
                        new SimpleFieldPortrayalFactory(
                            "Bottom trawler regulations",
                            new RegulationGridPortrayalFactory(
                                scenario.component("bottomTrawlerRegulations"),
                                scenario.component("vesselField"),
                                scenario.component("bathymetricGrid"),
                                WIDTH,
                                HEIGHT
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
                    WIDTH,
                    HEIGHT,
                    WHITE
                )
            )
        );
    }

    static void main(final String[] args) {
        final NorthwesternMedScenarioWithUI northwesternMedScenarioWithUI =
            new NorthwesternMedScenarioWithUI(new NorthwesternMedScenario().get());
        northwesternMedScenarioWithUI.createController();
    }

}
