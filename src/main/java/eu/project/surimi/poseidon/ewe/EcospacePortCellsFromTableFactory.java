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

import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.geography.grids.Cell;

/**
 * A {@link RelativeScopeFactory} counterpart of
 * {@link EcospacePortCells#fromTable(Table, EcospaceBasemap)}, built via
 * {@link Factories Factories.ecospacePortCellsFromTable(...)/Factories.ecospacePortCells(...)}.
 */
@Getter
@ToString
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class EcospacePortCellsFromTableFactory<S extends Scope> extends RelativeScopeFactory<
  S,
  ImmutableSet<Cell>
> {

  private final Factory<? super S, ? extends Table> table;
  private final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap;

  @Override
  protected ImmutableSet<Cell> newInstance(final S scope) {
    return EcospacePortCells.fromTable(
      table.get(scope),
      ecospaceBasemap.get(scope)
    );
  }
}
