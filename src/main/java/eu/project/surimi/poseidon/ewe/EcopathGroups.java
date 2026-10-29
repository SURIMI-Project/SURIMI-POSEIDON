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
 * Reads the functional groups of an Ecopath model from a table with the columns
 * of EwE's {@code EcopathGroup} table, wherever that table comes from. That
 * table has one row per group:
 * <ul>
 * <li>{@code GroupID} is the group's ID.</li>
 * <li>{@code GroupName} is the group's name.</li>
 * </ul>
 * Column names are matched case-insensitively, and column types are not
 * prescribed (see {@link EweColumns}).
 */
public final class EcopathGroups {

  /**
   * The name of the EwE table that holds the functional groups of an Ecopath
   * model.
   */
  public static final String TABLE_NAME = "EcopathGroup";

  private static final String GROUP_ID = "GroupID";
  private static final String GROUP_NAME = "GroupName";

  private EcopathGroups() {}

  /**
   * @param table a table with EwE's {@code EcopathGroup} columns
   * @return the groups, in the order of the table's rows
   * @throws IllegalArgumentException if the table has no rows, a column is
   *                                  missing or has the wrong kind of values,
   *                                  an ID is missing or not a positive whole
   *                                  number, a name is missing or blank, or two
   *                                  groups have the same ID
   */
  public static ImmutableList<EcopathGroup> fromTable(
    @NonNull final Table table
  ) {
    checkArgument(table.rowCount() > 0, "Table %s has no groups", table.name());
    final ImmutableList<EcopathGroup> groups = IntStream.range(
      0,
      table.rowCount()
    )
      .mapToObj(row ->
        new EcopathGroup(
          wholeNumber(table, GROUP_ID, row),
          string(table, GROUP_NAME, row)
        )
      )
      .collect(ImmutableList.toImmutableList());
    final Set<Integer> ids = new HashSet<>();
    groups.forEach(group ->
      checkArgument(
        ids.add(group.getId()),
        "More than one group with ID %s in table %s",
        group.getId(),
        table.name()
      )
    );
    return groups;
  }
}
