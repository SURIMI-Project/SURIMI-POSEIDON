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

import lombok.NonNull;
import lombok.Value;

/**
 * A functional group of an Ecopath model, as in a row of EwE's
 * {@code EcopathGroup} table: a species, a life stage of one, a set of species,
 * or detritus.
 */
@Value
public class EcopathGroup {

  /** The group's ID, EwE's {@code GroupID}. */
  int id;

  /** The group's name, EwE's {@code GroupName}. */
  @NonNull
  String name;

  /**
   * @param id   the group's ID
   * @param name the group's name
   * @throws IllegalArgumentException if the ID is not positive or the name is
   *                                  blank
   */
  public EcopathGroup(final int id, @NonNull final String name) {
    checkArgument(id > 0, "The group ID must be positive, but is %s", id);
    checkArgument(!name.isBlank(), "The name of group %s is blank", id);
    this.id = id;
    this.name = name;
  }
}
