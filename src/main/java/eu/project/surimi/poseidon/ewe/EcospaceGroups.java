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
import static eu.project.surimi.poseidon.ewe.EweColumns.booleanValue;
import static eu.project.surimi.poseidon.ewe.EweColumns.column;
import static eu.project.surimi.poseidon.ewe.EweColumns.number;
import static eu.project.surimi.poseidon.ewe.EweColumns.optionalString;
import static eu.project.surimi.poseidon.ewe.EweColumns.wholeNumber;
import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableMap;
import java.util.stream.IntStream;
import lombok.NonNull;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

/**
 * Reads the groups of an Ecospace scenario from a table with the columns of
 * EwE's {@code EcospaceScenarioGroup} table, wherever that table comes from.
 * That table has one row per Ecospace scenario and group:
 * <ul>
 * <li>{@code ScenarioID} is the ID of the Ecospace scenario, as in
 * {@link EcospaceBasemap#getScenarioId()}.</li>
 * <li>{@code GroupID} is the group's ID in the Ecospace scenario, which other
 * Ecospace tables, such as {@code EcospaceScenarioGroupHabitat}, refer to it
 * by.</li>
 * <li>{@code EcopathGroupID} is the ID of the Ecopath group.</li>
 * <li>{@code CapacityCalType} is how Ecospace works out the group's habitat
 * capacity (see {@link EcospaceGroup}).</li>
 * <li>{@code IsMigratory} is whether the group migrates.</li>
 * <li>{@code CapacityMap} is the group's capacity input map, as a masked map
 * string (see {@link EcospaceBasemap#decodeMaskedMap(String)}). A missing value,
 * or an empty string, is 1 in every cell, as in EwE.</li>
 * </ul>
 * Column names are matched case-insensitively, and column types are not
 * prescribed (see {@link EweColumns}).
 */
public final class EcospaceGroups {

  /**
   * The name of the EwE table that holds the groups of Ecospace scenarios.
   */
  public static final String TABLE_NAME = "EcospaceScenarioGroup";

  private static final String SCENARIO_ID = "ScenarioID";
  private static final String GROUP_ID = "GroupID";
  private static final String ECOPATH_GROUP_ID = "EcopathGroupID";
  private static final String CAPACITY_CAL_TYPE = "CapacityCalType";
  private static final String IS_MIGRATORY = "IsMigratory";
  private static final String CAPACITY_MAP = "CapacityMap";

  private EcospaceGroups() {}

  /**
   * @param table           a table with EwE's {@code EcospaceScenarioGroup}
   *                        columns
   * @param ecospaceBasemap the base map of the Ecospace scenario, which gives
   *                        its ID and the cells of its masked maps
   * @return the scenario's groups, by the ID of their Ecopath group, in the
   *         order of the table's rows
   * @throws IllegalArgumentException if a column is missing or has the wrong
   *                                  kind of values, an ID is not a positive
   *                                  whole number, a capacity calculation type
   *                                  is not one EwE defines, a capacity map
   *                                  doesn't match the base map, two groups
   *                                  stand for the same Ecopath group, or the
   *                                  scenario has no groups
   */
  public static ImmutableMap<Integer, EcospaceGroup> fromTable(
    @NonNull final Table table,
    @NonNull final EcospaceBasemap ecospaceBasemap
  ) {
    final Column<?> scenarioIds = column(table, SCENARIO_ID);
    final ImmutableMap<Integer, EcospaceGroup> groups = IntStream.range(
      0,
      table.rowCount()
    )
      .filter(
        row ->
          !scenarioIds.isMissing(row) &&
          number(scenarioIds, row) == ecospaceBasemap.getScenarioId()
      )
      .mapToObj(row -> group(table, row, ecospaceBasemap))
      .collect(
        ImmutableMap.toImmutableMap(
          EcospaceGroup::getEcopathGroupId,
          group -> group
        )
      );
    checkArgument(
      !groups.isEmpty(),
      "No groups for Ecospace scenario %s in table %s",
      ecospaceBasemap.getScenarioId(),
      table.name()
    );
    return groups;
  }

  private static EcospaceGroup group(
    final Table table,
    final int row,
    final EcospaceBasemap ecospaceBasemap
  ) {
    final double capacityCalculationType = number(
      table,
      CAPACITY_CAL_TYPE,
      row
    );
    checkArgument(
      capacityCalculationType >= 0 &&
        capacityCalculationType == Math.rint(capacityCalculationType),
      "%s must be a non-negative whole number, but is %s",
      CAPACITY_CAL_TYPE,
      capacityCalculationType
    );
    checkArgument(
      !column(table, IS_MIGRATORY).isMissing(row),
      "No value in column %s",
      IS_MIGRATORY
    );
    return new EcospaceGroup(
      wholeNumber(table, GROUP_ID, row),
      wholeNumber(table, ECOPATH_GROUP_ID, row),
      (int) capacityCalculationType,
      booleanValue(table, IS_MIGRATORY, row, false),
      optionalString(table, CAPACITY_MAP, row)
        .filter(not(String::isBlank))
        .map(ecospaceBasemap::decodeMaskedMap)
        .orElseGet(() -> ecospaceBasemap.uniformMaskedMap(1))
    );
  }
}
