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

import static eu.project.surimi.poseidon.ewe.EcospaceBasemap.DEGREE_TO_KM;
import static eu.project.surimi.poseidon.ewe.EcospaceBasemapTest.ANCHOVY_BAY;
import static eu.project.surimi.poseidon.ewe.Factories.bathymetricGridFromEcospaceBasemap;
import static eu.project.surimi.poseidon.ewe.Factories.ecospaceBasemap;
import static eu.project.surimi.poseidon.ewe.Factories.eweDatabase;
import static eu.project.surimi.poseidon.ewe.Factories.modelGridFromEcospaceBasemap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static uk.ac.ox.poseidon.core.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.io.paths.Factories.path;

import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.geography.Coordinate;
import uk.ac.ox.poseidon.geography.Envelope;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.grids.Cell;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

class EcospaceBasemapFactoriesTest {

  private static final double CELL_SIZE = 20 / DEGREE_TO_KM;

  private final EcospaceBasemapFromTableFactory<Scope> basemap =
    ecospaceBasemap(
      eweDatabase(path(ANCHOVY_BAY)),
      "BayOfAnchovies"
    );
  private final ModelGridFromEcospaceBasemapFactory<Scope> modelGrid =
    modelGridFromEcospaceBasemap(basemap);

  @Test
  void theModelGridHasTheBasemapDimensionsAndEnvelope() {
    final ModelGrid grid = modelGrid.get(GLOBAL_SCOPE);
    assertThat(grid.getGridWidth()).isEqualTo(20);
    assertThat(grid.getGridHeight()).isEqualTo(20);
    assertThat(grid.getEnvelope().getMinX()).isCloseTo(0, within(1e-12));
    assertThat(grid.getEnvelope().getMaxX()).isCloseTo(
      20 * CELL_SIZE,
      within(1e-12)
    );
    assertThat(grid.getEnvelope().getMinY()).isCloseTo(
      -20 * CELL_SIZE,
      within(1e-12)
    );
    assertThat(grid.getEnvelope().getMaxY()).isCloseTo(0, within(1e-12));
  }

  @Test
  void eweRowsAndColumnsAreModelGridYAndX() {
    final ModelGrid grid = modelGrid.get(GLOBAL_SCOPE);
    // the centre of EwE's row 1, column 11
    final Coordinate coordinate = new Coordinate(
      11.5 * CELL_SIZE,
      -1.5 * CELL_SIZE
    );
    assertThat(grid.toCell(coordinate)).isEqualTo(new Cell(11, 1));
  }

  @Test
  void theBathymetryHasTheBasemapDepths() {
    final EcospaceBasemap ecospaceBasemap = basemap.get(GLOBAL_SCOPE);
    final BathymetricGrid bathymetry = bathymetricGridFromEcospaceBasemap(
      basemap,
      modelGrid
    ).get(GLOBAL_SCOPE);
    assertThat(bathymetry.getModelGrid()).isSameAs(modelGrid.get(GLOBAL_SCOPE));
    for (int row = 0; row < 20; row++) {
      for (int col = 0; col < 20; col++) {
        final Cell cell = new Cell(col, row);
        final double depth = ecospaceBasemap.getDepth(row, col);
        assertThat(bathymetry.getDepth(cell)).isEqualTo(depth);
        assertThat(bathymetry.isWater(cell)).isEqualTo(depth > 0);
      }
    }
    assertThat(bathymetry.isLand(new Cell(0, 0))).isTrue();
    assertThat(bathymetry.getDepth(new Cell(11, 1))).isEqualTo(3);
    assertThat(bathymetry.getDepth(new Cell(19, 19))).isEqualTo(490);
  }

  @Test
  void rejectsAModelGridThatDoesNotMatchTheBasemap() {
    final Envelope envelope = basemap.get(GLOBAL_SCOPE).getEnvelope();
    assertThatThrownBy(() ->
      bathymetricGridFromEcospaceBasemap(basemap, _ ->
        ModelGrid.create(19, 20, envelope)
      ).get(GLOBAL_SCOPE)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("19 x 20");
    final Envelope shifted = new Envelope(
      envelope.getMinX() + CELL_SIZE / 2,
      envelope.getMaxX() + CELL_SIZE / 2,
      envelope.getMinY(),
      envelope.getMaxY()
    );
    assertThatThrownBy(() ->
      bathymetricGridFromEcospaceBasemap(basemap, _ ->
        ModelGrid.create(20, 20, shifted)
      ).get(GLOBAL_SCOPE)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("covers");
  }
}
