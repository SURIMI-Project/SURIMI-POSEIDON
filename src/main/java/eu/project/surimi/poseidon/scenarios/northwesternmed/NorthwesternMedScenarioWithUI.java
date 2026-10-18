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

import static eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario.BOTTOM_TRAWLER_GEAR_CODE;
import static eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario.PURSE_SEINE_GEAR_CODE;
import static java.awt.Color.WHITE;
import static uk.ac.ox.poseidon.core.predicates.Factories.condition;
import static uk.ac.ox.poseidon.core.predicates.Factories.equal;
import static uk.ac.ox.poseidon.core.providers.temporal.Factories.currentDateTime;
import static uk.ac.ox.poseidon.core.providers.temporal.Factories.hasDateTime;
import static uk.ac.ox.poseidon.core.quantities.Factories.massOf;
import static uk.ac.ox.poseidon.core.utils.Factories.listOf;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.stringAttribute;

import java.util.List;
import uk.ac.ox.poseidon.biology.biomass.CarryingCapacityGridFactory;
import uk.ac.ox.poseidon.biology.biomass.Factories;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.SimulationScope;
import uk.ac.ox.poseidon.fishing.vessels.VesselComponent;
import uk.ac.ox.poseidon.fishing.vessels.VesselSpecificationFactory;
import uk.ac.ox.poseidon.fishing.vessels.gears.Gear;
import uk.ac.ox.poseidon.gui.DisplayWrapper2D;
import uk.ac.ox.poseidon.gui.ScenarioWithUI;
import uk.ac.ox.poseidon.gui.portrayals.*;

/**
 * GUI wrapper for {@link NorthwesternMedScenario}: displays bathymetry, species
 * biomass, markets, ports, vessels, per-fleet regulation coverage, and grid
 * coordinates over the scenario's model grid.
 */
public class NorthwesternMedScenarioWithUI extends ScenarioWithUI {

  private static final int WIDTH = 1090;
  private static final int HEIGHT = 820;

  // purely a color-scale ceiling for the per-species biomass panel below;
  // decoupled from the scenario's own (now zero-initialized) biomass state.
  // 700 t comfortably covers the largest single-cell biomass value seen across
  // every species and every yearly snapshot in
  // inputs/northwestern_med/biomass_grids.nc (BOY tops out at ~673.9 t), so the
  // scale doesn't clip the most abundant stocks.
  private static final String DISPLAY_CARRYING_CAPACITY = "700000 kg";

  @SuppressWarnings({ "unchecked", "rawtypes" })
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
            new SimpleFieldPortrayalFactory<>(
              "Markets",
              new MarketGridPortrayalFactory(scenario.component("marketGrid")),
              true
            ),
            new SimpleFieldPortrayalFactory<>(
              "Ports",
              new PortGridPortrayalFactory(scenario.component("portGrid")),
              true
            ),
            new SimpleFieldPortrayalFactory<>(
              "Vessels",
              new VesselFieldPortrayalFactory(
                scenario.component("vesselField")
              ),
              true
            ),
            new SimpleFieldPortrayalFactory<>(
              "Purse seiner regulations",
              RegulationGridPortrayalFactory.builder()
                .gear(gearOf(scenario))
                .regulations(scenario.component("purseSeinerRegulations"))
                .vesselField(scenario.component("vesselField"))
                .fleet(
                  condition(
                    stringAttribute("main_fishing_gear"),
                    equal(PURSE_SEINE_GEAR_CODE)
                  )
                )
                .bathymetric(scenario.component("bathymetricGrid"))
                .currentDateTime(currentDateTime())
                .hasDateTime(hasDateTime())
                .displayWidth(WIDTH)
                .displayHeight(HEIGHT)
                .build(),
              true
            ),
            new SimpleFieldPortrayalFactory<>(
              "Bottom trawler regulations",
              RegulationGridPortrayalFactory.builder()
                .gear(gearOf(scenario))
                .regulations(scenario.component("bottomTrawlerRegulations"))
                .vesselField(scenario.component("vesselField"))
                .fleet(
                  condition(
                    stringAttribute("main_fishing_gear"),
                    equal(BOTTOM_TRAWLER_GEAR_CODE)
                  )
                )
                .bathymetric(scenario.component("bathymetricGrid"))
                .currentDateTime(currentDateTime())
                .hasDateTime(hasDateTime())
                .displayWidth(WIDTH)
                .displayHeight(HEIGHT)
                .build(),
              true
            ),
            new SimpleFieldPortrayalFactory<>(
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

  // The gear component, taken by name from the vessel specification, so that
  // the regulation portrayals judge the gear the vessels fish with
  @SuppressWarnings("unchecked")
  private static VesselComponent<? extends Gear> gearOf(
    final Scenario scenario
  ) {
    return (VesselComponent<? extends Gear>) scenario
      .component("vesselSpecification", VesselSpecificationFactory.class)
      .getComponents()
      .get("gear");
  }

  /**
   * Opens the GUI for a freshly built {@link LocalNorthwesternMedScenario}.
   */
  static void main(final String[] args) {
    final NorthwesternMedScenarioWithUI northwesternMedScenarioWithUI =
      new NorthwesternMedScenarioWithUI(
        new LocalNorthwesternMedScenario().get()
      );
    northwesternMedScenarioWithUI.createController();
  }
}
