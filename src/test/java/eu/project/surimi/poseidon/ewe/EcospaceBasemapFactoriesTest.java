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

package eu.project.surimi.poseidon.ewe;

import static eu.project.surimi.poseidon.ewe.Factories.bathymetricGridFromEcospaceBasemap;
import static eu.project.surimi.poseidon.ewe.Factories.modelGridFromEcospaceBasemap;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;

import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.geography.Envelope;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.grids.Cell;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

class EcospaceBasemapFactoriesTest {

  @Test
  void theCellsTheBasemapExcludesAreInactive() {
    // A 2 x 3 base map, all water except for land at EwE's row 0, column 0,
    // which excludes row 0, column 1 and row 1, column 2
    final EcospaceBasemap excluding = new EcospaceBasemap(
      1,
      2,
      3,
      new Envelope(0, 3, -2, 0),
      new double[][] { { 0, 5, 5 }, { 5, 5, 5 } },
      new boolean[][] { { false, true, false }, { false, false, true } }
    );
    final ModelGridFromEcospaceBasemapFactory<Scope> excludingGrid =
      modelGridFromEcospaceBasemap(_ -> excluding);
    final ModelGrid grid = excludingGrid.get(GLOBAL_SCOPE);
    assertThat(grid.getActiveCells()).containsExactlyInAnyOrder(
      new Cell(0, 0),
      new Cell(2, 0),
      new Cell(0, 1),
      new Cell(1, 1)
    );
    final BathymetricGrid bathymetry = bathymetricGridFromEcospaceBasemap(
      _ -> excluding,
      excludingGrid
    ).get(GLOBAL_SCOPE);
    assertThat(bathymetry.getActiveWaterCells()).containsExactlyInAnyOrder(
      new Cell(2, 0),
      new Cell(0, 1),
      new Cell(1, 1)
    );
  }
}
