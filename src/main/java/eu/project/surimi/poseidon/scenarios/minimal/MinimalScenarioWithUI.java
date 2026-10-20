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

import static java.awt.Color.WHITE;
import static java.util.Collections.nCopies;
import static uk.ac.ox.poseidon.core.utils.Factories.listOf;

import java.util.List;
import uk.ac.ox.poseidon.biology.biomass.CarryingCapacityGrid;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.SimulationScope;
import uk.ac.ox.poseidon.core.utils.ListFactory;
import uk.ac.ox.poseidon.gui.DisplayWrapper2D;
import uk.ac.ox.poseidon.gui.ScenarioWithUI;
import uk.ac.ox.poseidon.gui.portrayals.*;

/**
 * GUI wrapper for {@link MinimalScenario}: displays bathymetry, species
 * biomass, markets, ports, vessels, and grid coordinates over the scenario's
 * model grid.
 */
public class MinimalScenarioWithUI extends ScenarioWithUI {

  public MinimalScenarioWithUI(final Scenario scenario) {
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
              scenario.component("biomassGrids"),
              carryingCapacityGridPerSpecies(scenario),
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

  // Every species shares the one carrying capacity grid, which the portrayal
  // pairs with each species' biomass grid in turn
  private static ListFactory<
    SimulationScope,
    CarryingCapacityGrid
  > carryingCapacityGridPerSpecies(final Scenario scenario) {
    final Factory<SimulationScope, CarryingCapacityGrid> carryingCapacityGrid =
      scenario.component("carryingCapacityGrid");
    return listOf(
      nCopies(
        MinimalScenario.LIFE_STAGE_PER_SPECIES_CODE.size(),
        carryingCapacityGrid
      )
    );
  }

  /**
   * Opens the GUI for a freshly built {@link MinimalScenario}.
   */
  static void main(final String[] args) {
    final MinimalScenarioWithUI minimalScenarioWithUI =
      new MinimalScenarioWithUI(new MinimalScenario().get());
    minimalScenarioWithUI.createController();
  }
}
