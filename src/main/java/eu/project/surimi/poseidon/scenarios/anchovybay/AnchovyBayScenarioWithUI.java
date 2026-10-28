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

package eu.project.surimi.poseidon.scenarios.anchovybay;

import static java.awt.Color.WHITE;

import java.util.List;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.gui.DisplayWrapper2D;
import uk.ac.ox.poseidon.gui.ScenarioWithUI;
import uk.ac.ox.poseidon.gui.portrayals.BathymetryFieldPortrayalFactory;
import uk.ac.ox.poseidon.gui.portrayals.CoordinatesPortrayalFactory;
import uk.ac.ox.poseidon.gui.portrayals.SimpleFieldPortrayalFactory;

/**
 * GUI wrapper for {@link AnchovyBayScenario}: displays bathymetry and grid
 * coordinates over the scenario's model grid.
 */
public class AnchovyBayScenarioWithUI extends ScenarioWithUI {

  /**
   * @param scenario an {@link AnchovyBayScenario}, whose components the
   *                 displays look up by name
   */
  public AnchovyBayScenarioWithUI(final Scenario scenario) {
    super(
      scenario,
      List.of(
        new DisplayWrapper2D(
          "Anchovy Bay",
          List.of(
            new BathymetryFieldPortrayalFactory(
              scenario.component("bathymetricGrid")
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

  /**
   * Opens the GUI for a freshly built {@link AnchovyBayScenario}.
   */
  static void main(final String[] args) {
    final AnchovyBayScenarioWithUI anchovyBayScenarioWithUI =
      new AnchovyBayScenarioWithUI(new AnchovyBayScenario().get());
    anchovyBayScenarioWithUI.createController();
  }
}
