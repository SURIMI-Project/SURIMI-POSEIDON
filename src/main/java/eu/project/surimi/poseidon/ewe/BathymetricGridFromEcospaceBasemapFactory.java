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

import static com.google.common.base.Preconditions.checkArgument;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.geography.Envelope;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.bathymetry.DefaultBathymetricGrid;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

/**
 * A {@link RelativeScopeFactory} for the {@link BathymetricGrid} of an
 * {@link EcospaceBasemap}, over a model grid that must have the base map's
 * dimensions and envelope (such as the one from
 * {@link Factories#modelGridFromEcospaceBasemap(Factory)}). EwE cell
 * {@code (row, col)} is model grid cell {@code (x = col, y = row)}, and its
 * elevation is minus its depth, so EwE's land (depth 0) is land in POSEIDON
 * too. Built via
 * {@link Factories#bathymetricGridFromEcospaceBasemap(Factory, Factory)}.
 */
@Getter
@ToString
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class BathymetricGridFromEcospaceBasemapFactory<S extends Scope> extends RelativeScopeFactory<
  S,
  BathymetricGrid
> {

  /** How far, in cells, the model grid's edges may be from the base map's. */
  private static final double EDGE_TOLERANCE_IN_CELLS = 1e-6;

  private final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap;
  private final Factory<? super S, ? extends ModelGrid> modelGrid;

  private static boolean sameEnvelope(
    final Envelope a,
    final Envelope b,
    final double tolerance
  ) {
    return (
      Math.abs(a.getMinX() - b.getMinX()) <= tolerance &&
      Math.abs(a.getMaxX() - b.getMaxX()) <= tolerance &&
      Math.abs(a.getMinY() - b.getMinY()) <= tolerance &&
      Math.abs(a.getMaxY() - b.getMaxY()) <= tolerance
    );
  }

  @Override
  protected BathymetricGrid newInstance(final S scope) {
    final EcospaceBasemap basemap = ecospaceBasemap.get(scope);
    final ModelGrid grid = modelGrid.get(scope);
    checkArgument(
      grid.getGridWidth() == basemap.getCols() &&
        grid.getGridHeight() == basemap.getRows(),
      "The model grid is %s x %s cells, but the Ecospace base map is %s x %s",
      grid.getGridWidth(),
      grid.getGridHeight(),
      basemap.getCols(),
      basemap.getRows()
    );
    checkArgument(
      sameEnvelope(
        grid.getEnvelope(),
        basemap.getEnvelope(),
        (EDGE_TOLERANCE_IN_CELLS * basemap.getEnvelope().getWidth()) /
          basemap.getCols()
      ),
      "The model grid covers %s, but the Ecospace base map covers %s",
      grid.getEnvelope(),
      basemap.getEnvelope()
    );
    final double[][] elevations =
      new double[basemap.getCols()][basemap.getRows()];
    for (int row = 0; row < basemap.getRows(); row++) {
      for (int col = 0; col < basemap.getCols(); col++) {
        elevations[col][row] = -basemap.getDepth(row, col);
      }
    }
    return new DefaultBathymetricGrid(grid, elevations);
  }
}
