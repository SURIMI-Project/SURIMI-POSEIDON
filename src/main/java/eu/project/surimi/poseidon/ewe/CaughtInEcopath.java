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

import java.util.function.BiFunction;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A yes/no mapping of Ecopath groups, whatever the context, that is true iff
 * some fleet lands or discards some of the group in the Ecopath model (see
 * {@link EcopathCatches#isCaught(EcopathGroup)}). Built via
 * {@link Factories#caughtInEcopath(uk.ac.ox.poseidon.core.Factory)}.
 */
@RequiredArgsConstructor
public class CaughtInEcopath
  implements BiFunction<Object, EcopathGroup, Boolean>
{

  @NonNull
  private final EcopathCatches ecopathCatches;

  @Override
  public Boolean apply(final Object context, final EcopathGroup group) {
    return ecopathCatches.isCaught(group);
  }
}
