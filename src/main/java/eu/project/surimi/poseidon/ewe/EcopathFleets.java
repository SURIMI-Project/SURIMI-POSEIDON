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
import static eu.project.surimi.poseidon.ewe.EweColumns.string;
import static eu.project.surimi.poseidon.ewe.EweColumns.wholeNumber;

import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.NonNull;
import tech.tablesaw.api.Table;

/**
 * Reads the fleets of an Ecopath model from a table with the columns of EwE's
 * {@code EcopathFleet} table, wherever that table comes from. That table has
 * one row per fleet:
 * <ul>
 * <li>{@code FleetID} is the fleet's ID.</li>
 * <li>{@code FleetName} is the fleet's name.</li>
 * </ul>
 * The fleets' costs and nominal effort are not read yet. Column names are
 * matched case-insensitively, and column types are not prescribed (see
 * {@link EweColumns}).
 */
public final class EcopathFleets {

  /** The name of the EwE table that holds the fleets of an Ecopath model. */
  public static final String TABLE_NAME = "EcopathFleet";

  private static final String FLEET_ID = "FleetID";
  private static final String FLEET_NAME = "FleetName";

  private EcopathFleets() {}

  /**
   * @param table a table with EwE's {@code EcopathFleet} columns
   * @return the fleets, in the order of the table's rows
   * @throws IllegalArgumentException if the table has no rows, a column is
   *                                  missing or has the wrong kind of values,
   *                                  an ID is missing or not a positive whole
   *                                  number, a name is missing or blank, or two
   *                                  fleets have the same ID
   */
  public static ImmutableList<EcopathFleet> fromTable(
    @NonNull final Table table
  ) {
    checkArgument(table.rowCount() > 0, "Table %s has no fleets", table.name());
    final ImmutableList<EcopathFleet> fleets = IntStream.range(
      0,
      table.rowCount()
    )
      .mapToObj(row ->
        new EcopathFleet(
          wholeNumber(table, FLEET_ID, row),
          string(table, FLEET_NAME, row)
        )
      )
      .collect(ImmutableList.toImmutableList());
    final Set<Integer> ids = new HashSet<>();
    fleets.forEach(fleet ->
      checkArgument(
        ids.add(fleet.getId()),
        "More than one fleet with ID %s in table %s",
        fleet.getId(),
        table.name()
      )
    );
    return fleets;
  }
}
