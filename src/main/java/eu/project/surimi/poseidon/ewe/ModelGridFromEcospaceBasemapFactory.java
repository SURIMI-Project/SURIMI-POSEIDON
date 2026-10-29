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

import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.geography.grids.Cell;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

/**
 * A {@link RelativeScopeFactory} for the {@link ModelGrid} of an
 * {@link EcospaceBasemap}: a grid of the base map's columns and rows over its
 * envelope, whose active cells are those the base map doesn't exclude
 * ({@link EcospaceBasemap#isExcluded}). EwE's row 0 is the northern edge, as is a
 * model grid's {@code y = 0}, so EwE cell {@code (row, col)} is model grid cell
 * {@code (x = col, y = row)}. Built via
 * {@link Factories#modelGridFromEcospaceBasemap(Factory)}.
 */
@Getter
@ToString
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class ModelGridFromEcospaceBasemapFactory<S extends Scope> extends RelativeScopeFactory<
  S,
  ModelGrid
> {

  private final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap;

  @Override
  protected ModelGrid newInstance(final S scope) {
    final EcospaceBasemap basemap = ecospaceBasemap.get(scope);
    return ModelGrid.create(
      basemap.getCols(),
      basemap.getRows(),
      basemap.getEnvelope()
    ).withActiveCells(
      IntStream.range(0, basemap.getRows())
        .boxed()
        .flatMap(row ->
          IntStream.range(0, basemap.getCols())
            .filter(col -> !basemap.isExcluded(row, col))
            .mapToObj(col -> new Cell(col, row))
        )
        .toList()
    );
  }
}
