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

import com.google.common.collect.ImmutableSet;
import java.util.stream.IntStream;
import lombok.NonNull;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import uk.ac.ox.poseidon.geography.grids.Cell;

/**
 * Reads the port cells of an Ecospace scenario from a table with the columns of
 * EwE's {@code EcospaceScenarioFleet} table, wherever that table comes from.
 * That table has one row per Ecospace scenario and fleet:
 * <ul>
 * <li>{@code ScenarioID} is the ID of the Ecospace scenario, as in
 * {@link EcospaceBasemap#getScenarioId()}.</li>
 * <li>{@code PortMap} is the fleet's ports, as a full boolean map string (see
 * {@link EweMaps}) of the base map's dimensions. A missing value is a fleet
 * without ports, as in EwE.</li>
 * </ul>
 * Ports have no names or other attributes in EwE, which uses a fleet's port
 * cells only to compute its sailing costs. Column names are matched
 * case-insensitively, and column types are not prescribed (see
 * {@link EweColumns}).
 */
public final class EcospacePortCells {

  /**
   * The name of the EwE table that holds the fleets of Ecospace scenarios,
   * and their port maps.
   */
  public static final String TABLE_NAME = "EcospaceScenarioFleet";

  private static final String SCENARIO_ID = "ScenarioID";
  private static final String PORT_MAP = "PortMap";

  private EcospacePortCells() {}

  /**
   * @param table           a table with EwE's {@code EcospaceScenarioFleet}
   *                        columns
   * @param ecospaceBasemap the base map of the Ecospace scenario, which gives
   *                        its ID and the dimensions of its maps
   * @return the cells that are a port of at least one of the scenario's fleets,
   *         in row-major order, EwE's row and column being the cell's
   *         {@code y} and {@code x}
   * @throws IllegalArgumentException if a column is missing or has the wrong
   *                                  kind of values, a port map doesn't match
   *                                  the base map's dimensions, or the
   *                                  scenario has no port cells
   */
  public static ImmutableSet<Cell> fromTable(
    @NonNull final Table table,
    @NonNull final EcospaceBasemap ecospaceBasemap
  ) {
    final int rows = ecospaceBasemap.getRows();
    final int cols = ecospaceBasemap.getCols();
    final Column<?> scenarioIds = column(table, SCENARIO_ID);
    final Column<?> portMaps = column(table, PORT_MAP);
    final boolean[][] isPort = new boolean[rows][cols];
    IntStream.range(0, table.rowCount())
      .filter(
        i ->
          !scenarioIds.isMissing(i) &&
          number(scenarioIds, i) == ecospaceBasemap.getScenarioId() &&
          !portMaps.isMissing(i)
      )
      .mapToObj(i ->
        EweMaps.decodeBooleanMap(portMaps.getString(i), rows, cols)
      )
      .forEach(portMap -> {
        for (int row = 0; row < rows; row++) {
          for (int col = 0; col < cols; col++) {
            isPort[row][col] |= portMap[row][col];
          }
        }
      });
    final ImmutableSet<Cell> cells = IntStream.range(0, rows)
      .boxed()
      .flatMap(row ->
        IntStream.range(0, cols)
          .filter(col -> isPort[row][col])
          .mapToObj(col -> new Cell(col, row))
      )
      .collect(ImmutableSet.toImmutableSet());
    checkArgument(
      !cells.isEmpty(),
      "No port cells for Ecospace scenario %s in table %s",
      ecospaceBasemap.getScenarioId(),
      table.name()
    );
    return cells;
  }
}
