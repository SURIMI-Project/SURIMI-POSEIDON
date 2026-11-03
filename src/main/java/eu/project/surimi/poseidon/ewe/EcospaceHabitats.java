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
import static eu.project.surimi.poseidon.ewe.EweColumns.column;
import static eu.project.surimi.poseidon.ewe.EweColumns.number;
import static eu.project.surimi.poseidon.ewe.EweColumns.optionalString;
import static eu.project.surimi.poseidon.ewe.EweColumns.string;
import static eu.project.surimi.poseidon.ewe.EweColumns.wholeNumber;
import static java.util.Comparator.comparingDouble;
import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Tables;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.NonNull;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

/**
 * The habitats of an Ecospace scenario, and the habitats each of its groups
 * prefers, read from tables with the columns of EwE's
 * {@code EcospaceScenarioHabitat} and {@code EcospaceScenarioGroupHabitat}
 * tables, wherever they come from.
 * <p>
 * {@code EcospaceScenarioHabitat} has one row per Ecospace scenario and
 * habitat:
 * <ul>
 * <li>{@code ScenarioID} is the ID of the Ecospace scenario, as in
 * {@link EcospaceBasemap#getScenarioId()}.</li>
 * <li>{@code HabitatID} and {@code HabitatName} are the habitat's ID and
 * name.</li>
 * <li>{@code Sequence} orders the habitats. EwE treats the first one as the
 * whole of the map, whatever its name (usually "All"), and never reads its
 * map.</li>
 * <li>{@code HabitatMap} is the fraction of each cell the habitat covers, as a
 * masked map string (see {@link EcospaceBasemap#decodeMaskedMap(String)}). A
 * missing value, or an empty string, covers no cell, as in EwE.</li>
 * </ul>
 * {@code EcospaceScenarioGroupHabitat} has one row per Ecospace scenario,
 * group and preferred habitat: {@code ScenarioID}, {@code GroupID} (the
 * group's ID in the Ecospace scenario, see {@link EcospaceGroup}),
 * {@code HabitatID}, and {@code Preference}, which is 1 when missing, as in
 * EwE.
 * <p>
 * A group with no preferred habitat, or whose only preferred habitat is the
 * first one with a non-zero preference, uses the whole map. Any other group
 * uses each cell as much as the sum, over the habitats it prefers, of its
 * preference times the fraction of the cell the habitat covers
 * ({@code cEcoSpace.setHabCapFromHabitat}). Column names are matched
 * case-insensitively, and column types are not prescribed (see
 * {@link EweColumns}).
 */
public final class EcospaceHabitats {

  /**
   * The name of the EwE table that holds the habitats of Ecospace scenarios.
   */
  public static final String HABITAT_TABLE_NAME = "EcospaceScenarioHabitat";

  /**
   * The name of the EwE table that holds the habitats the groups of Ecospace
   * scenarios prefer.
   */
  public static final String GROUP_HABITAT_TABLE_NAME =
    "EcospaceScenarioGroupHabitat";

  private static final String SCENARIO_ID = "ScenarioID";
  private static final String HABITAT_ID = "HabitatID";
  private static final String HABITAT_NAME = "HabitatName";
  private static final String SEQUENCE = "Sequence";
  private static final String HABITAT_MAP = "HabitatMap";
  private static final String GROUP_ID = "GroupID";
  private static final String PREFERENCE = "Preference";

  /** The IDs of the habitats, in the order of their {@code Sequence}. */
  private final ImmutableList<Integer> habitatIds;

  /** The names of the habitats, by ID. */
  private final ImmutableMap<Integer, String> habitatNames;

  /**
   * The preferences of the groups, with an Ecospace group ID per row and a
   * habitat ID per column.
   */
  private final ImmutableTable<Integer, Integer, Double> preferences;

  private final ImmutableMap<Integer, double[][]> habitatMaps;

  private EcospaceHabitats(
    final ImmutableList<Integer> habitatIds,
    final ImmutableMap<Integer, String> habitatNames,
    final ImmutableTable<Integer, Integer, Double> preferences,
    final ImmutableMap<Integer, double[][]> habitatMaps
  ) {
    this.habitatIds = habitatIds;
    this.habitatNames = habitatNames;
    this.preferences = preferences;
    this.habitatMaps = habitatMaps;
  }

  /**
   * @param habitatTable      a table with EwE's
   *                          {@code EcospaceScenarioHabitat} columns
   * @param groupHabitatTable a table with EwE's
   *                          {@code EcospaceScenarioGroupHabitat} columns
   * @param ecospaceBasemap   the base map of the Ecospace scenario, which gives
   *                          its ID and the cells of its masked maps
   * @return the scenario's habitats and the groups' preferences
   * @throws IllegalArgumentException if a column is missing or has the wrong
   *                                  kind of values, the scenario has no
   *                                  habitats, two habitats have the same ID, a
   *                                  habitat map doesn't match the base map, a
   *                                  preference names a habitat the scenario
   *                                  doesn't have, is negative, or is given
   *                                  twice
   */
  public static EcospaceHabitats fromTables(
    @NonNull final Table habitatTable,
    @NonNull final Table groupHabitatTable,
    @NonNull final EcospaceBasemap ecospaceBasemap
  ) {
    final int scenarioId = ecospaceBasemap.getScenarioId();
    final ImmutableList<Integer> habitatRows = rowsOfScenario(
      habitatTable,
      scenarioId
    )
      .stream()
      .sorted(comparingDouble(row -> number(habitatTable, SEQUENCE, row)))
      .collect(ImmutableList.toImmutableList());
    checkArgument(
      !habitatRows.isEmpty(),
      "No habitats for Ecospace scenario %s in table %s",
      scenarioId,
      habitatTable.name()
    );
    final ImmutableList<Integer> habitatIds = habitatRows
      .stream()
      .map(row -> wholeNumber(habitatTable, HABITAT_ID, row))
      .collect(ImmutableList.toImmutableList());
    final ImmutableMap<Integer, String> habitatNames = habitatRows
      .stream()
      .collect(
        ImmutableMap.toImmutableMap(
          row -> wholeNumber(habitatTable, HABITAT_ID, row),
          row -> string(habitatTable, HABITAT_NAME, row)
        )
      );
    final ImmutableMap<Integer, double[][]> habitatMaps = habitatRows
      .stream()
      .collect(
        ImmutableMap.toImmutableMap(
          row -> wholeNumber(habitatTable, HABITAT_ID, row),
          row ->
            optionalString(habitatTable, HABITAT_MAP, row)
              .filter(not(String::isBlank))
              .map(ecospaceBasemap::decodeMaskedMap)
              .orElseGet(() -> ecospaceBasemap.uniformMaskedMap(0))
        )
      );
    final ImmutableTable<Integer, Integer, Double> preferences = rowsOfScenario(
      groupHabitatTable,
      scenarioId
    )
      .stream()
      .map(row -> preference(groupHabitatTable, row, habitatNames))
      .collect(
        ImmutableTable.toImmutableTable(
          com.google.common.collect.Table.Cell::getRowKey,
          com.google.common.collect.Table.Cell::getColumnKey,
          com.google.common.collect.Table.Cell::getValue
        )
      );
    return new EcospaceHabitats(
      habitatIds,
      habitatNames,
      preferences,
      habitatMaps
    );
  }

  private static ImmutableList<Integer> rowsOfScenario(
    final Table table,
    final int scenarioId
  ) {
    final Column<?> scenarioIds = column(table, SCENARIO_ID);
    return IntStream.range(0, table.rowCount())
      .filter(
        row ->
          !scenarioIds.isMissing(row) && number(scenarioIds, row) == scenarioId
      )
      .boxed()
      .collect(ImmutableList.toImmutableList());
  }

  private static com.google.common.collect.Table.Cell<
    Integer,
    Integer,
    Double
  > preference(
    final Table table,
    final int row,
    final Map<Integer, String> habitatNames
  ) {
    final int groupId = wholeNumber(table, GROUP_ID, row);
    final int habitatId = wholeNumber(table, HABITAT_ID, row);
    final double preference = number(table, PREFERENCE, row, 1);
    checkArgument(
      habitatNames.containsKey(habitatId),
      "Group %s prefers habitat %s, which the Ecospace scenario doesn't have",
      groupId,
      habitatId
    );
    checkArgument(
      preference >= 0,
      "The preference of group %s for habitat %s must not be negative, " +
        "but is %s",
      groupId,
      habitatId,
      preference
    );
    return Tables.immutableCell(groupId, habitatId, preference);
  }

  /**
   * How much a group uses each cell, from the habitats it prefers (see the
   * class documentation).
   *
   * @param ecospaceGroupId the group's ID in the Ecospace scenario
   * @return empty if the group uses the whole map; otherwise how much it uses
   *         each cell, indexed {@code [row][col]}, {@link Double#NaN} where
   *         the base map's depth is not positive
   * @throws IllegalArgumentException if the group prefers both the first
   *                                  habitat and others: EwE's result then
   *                                  depends on the order of the table's rows
   */
  public Optional<double[][]> habitatUse(final int ecospaceGroupId) {
    final Map<Integer, Double> groupPreferences = preferences.row(
      ecospaceGroupId
    );
    final int wholeMap = habitatIds.getFirst();
    checkArgument(
      !groupPreferences.containsKey(wholeMap) || groupPreferences.size() == 1,
      "Group %s prefers habitat %s, which stands for the whole map, and " +
        "others: EwE's capacity then depends on the order of the rows of %s",
      ecospaceGroupId,
      wholeMap,
      GROUP_HABITAT_TABLE_NAME
    );
    final boolean usesWholeMap =
      groupPreferences.isEmpty() ||
      groupPreferences
        .entrySet()
        .stream()
        .anyMatch(entry -> entry.getKey() == wholeMap && entry.getValue() != 0);
    return usesWholeMap
      ? Optional.empty()
      : Optional.of(weightedHabitatMaps(groupPreferences));
  }

  private double[][] weightedHabitatMaps(
    final Map<Integer, Double> groupPreferences
  ) {
    final double[][] firstMap = habitatMap(habitatIds.getFirst());
    final double[][] use = new double[firstMap.length][firstMap[0].length];
    for (int row = 0; row < use.length; row++) {
      for (int col = 0; col < use[row].length; col++) {
        final int r = row;
        final int c = col;
        // The first map is NaN where the depth is not positive, which a sum
        // with no terms would not be
        use[row][col] = Double.isNaN(firstMap[row][col])
          ? Double.NaN
          : groupPreferences
              .entrySet()
              .stream()
              .filter(entry -> !entry.getKey().equals(habitatIds.getFirst()))
              .mapToDouble(
                entry -> entry.getValue() * habitatMap(entry.getKey())[r][c]
              )
              .sum();
      }
    }
    return use;
  }

  private double[][] habitatMap(final int habitatId) {
    return Optional.ofNullable(habitatMaps.get(habitatId)).orElseThrow(() ->
      new IllegalStateException("No map for habitat " + habitatId)
    );
  }
}
