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
import static eu.project.surimi.poseidon.ewe.EweColumns.number;
import static eu.project.surimi.poseidon.ewe.EweColumns.wholeNumber;

import com.google.common.collect.ImmutableTable;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.Value;
import tech.tablesaw.api.Table;

/**
 * The Ecopath catch of each functional group by each fleet, read from a table
 * with the columns of EwE's {@code EcopathCatch} table, wherever that table
 * comes from. That table has one row per group and fleet:
 * <ul>
 * <li>{@code GroupID} is the ID of the group, as in
 * {@link EcopathGroup#getId()}.</li>
 * <li>{@code FleetID} is the ID of the fleet, as in
 * {@link EcopathFleet#getId()}.</li>
 * <li>{@code Landing} is what the fleet lands of the group (t/km²/year).</li>
 * <li>{@code Discards} is what the fleet discards of it (t/km²/year).</li>
 * </ul>
 * A group and fleet without a row have no catch, as in EwE, which fills its
 * catch arrays from the rows present. The catches are keyed by ID, and are not
 * checked against the model's groups and fleets. Prices and discard mortality
 * are not read yet. Column names are matched case-insensitively, and column
 * types are not prescribed (see {@link EweColumns}).
 */
@Value
public class EcopathCatches {

  /** The name of the EwE table that holds the Ecopath catches. */
  public static final String TABLE_NAME = "EcopathCatch";

  private static final String GROUP_ID = "GroupID";
  private static final String FLEET_ID = "FleetID";
  private static final String LANDING = "Landing";
  private static final String DISCARDS = "Discards";

  /** The landings, with a group ID per row and a fleet ID per column. */
  @NonNull
  ImmutableTable<Integer, Integer, Double> landings;

  /** The discards, with a group ID per row and a fleet ID per column. */
  @NonNull
  ImmutableTable<Integer, Integer, Double> discards;

  /**
   * @param table a table with EwE's {@code EcopathCatch} columns
   * @return the catches
   * @throws IllegalArgumentException if a column is missing or has the wrong
   *                                  kind of values, an ID is missing or not a
   *                                  positive whole number, a landing or
   *                                  discard is missing or negative, or two
   *                                  rows have the same group and fleet
   */
  public static EcopathCatches fromTable(@NonNull final Table table) {
    final ImmutableTable.Builder<Integer, Integer, Double> landings =
      ImmutableTable.builder();
    final ImmutableTable.Builder<Integer, Integer, Double> discards =
      ImmutableTable.builder();
    IntStream.range(0, table.rowCount()).forEach(row -> {
      final int groupId = wholeNumber(table, GROUP_ID, row);
      final int fleetId = wholeNumber(table, FLEET_ID, row);
      landings.put(groupId, fleetId, nonNegative(table, LANDING, row));
      discards.put(groupId, fleetId, nonNegative(table, DISCARDS, row));
    });
    return new EcopathCatches(
      buildWithoutDuplicates(landings, table),
      buildWithoutDuplicates(discards, table)
    );
  }

  private static double nonNegative(
    final Table table,
    final String name,
    final int row
  ) {
    final double value = number(table, name, row);
    checkArgument(
      value >= 0,
      "%s must not be negative, but is %s in row %s of table %s",
      name,
      value,
      row + 1,
      table.name()
    );
    return value;
  }

  private static ImmutableTable<
    Integer,
    Integer,
    Double
  > buildWithoutDuplicates(
    final ImmutableTable.Builder<Integer, Integer, Double> builder,
    final Table table
  ) {
    try {
      return builder.buildOrThrow();
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
        "More than one row for the same group and fleet in table " +
          table.name(),
        e
      );
    }
  }

  /**
   * @param groupId the group's ID
   * @param fleetId the fleet's ID
   * @return what the fleet lands of the group, 0 if there is no row for them
   */
  public double getLanding(final int groupId, final int fleetId) {
    return Objects.requireNonNullElse(landings.get(groupId, fleetId), 0.0);
  }

  /**
   * @param groupId the group's ID
   * @param fleetId the fleet's ID
   * @return what the fleet discards of the group, 0 if there is no row for
   *         them
   */
  public double getDiscards(final int groupId, final int fleetId) {
    return Objects.requireNonNullElse(discards.get(groupId, fleetId), 0.0);
  }

  /**
   * @param group a functional group
   * @return whether some fleet lands or discards some of the group
   */
  public boolean isCaught(@NonNull final EcopathGroup group) {
    return Stream.of(landings, discards)
      .flatMap(catches -> catches.row(group.getId()).values().stream())
      .anyMatch(c -> c > 0);
  }
}
