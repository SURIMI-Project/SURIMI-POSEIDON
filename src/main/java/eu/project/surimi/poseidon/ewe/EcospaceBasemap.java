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

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.NonNull;
import tech.tablesaw.api.BooleanColumn;
import tech.tablesaw.api.NumericColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import uk.ac.ox.poseidon.geography.Envelope;

/**
 * The base map of an Ecospace scenario of an EwE model: its grid of
 * {@code rows x cols} square cells, the geographic {@link Envelope} the grid
 * covers, and the depth of each cell. This mirrors EwE's
 * {@code cEcospaceBasemap}. Cells are indexed as in EwE, by row and column, row
 * 0 being the northernmost.
 * <p>
 * {@link #fromTable(Table, String)} reads a base map from a table with the
 * columns of EwE's {@code EcospaceScenario} table, wherever that table comes
 * from:
 * <ul>
 * <li>{@code ScenarioName} selects the row.</li>
 * <li>{@code Inrow} and {@code Incol} are the numbers of rows and columns.</li>
 * <li>{@code MinLat} and {@code MinLon} are the latitude and longitude of the
 * grid's <em>top-left</em> corner, despite their names (see
 * {@code cEcospaceBasemap}). Unlike {@code EcopathModel.MinLat}, {@code MinLat}
 * is the northern edge. A missing value is 0, as in EwE.</li>
 * <li>{@code CellLength} is the side of a cell, in km. The cell size in degrees
 * is {@code CellLength / DEGREE_TO_KM}, as EwE computes it. The table's
 * {@code CellSize} column is a leftover that EwE no longer reads, and is
 * ignored.</li>
 * <li>{@code AssumeSquareCells} must be false. When it is true (or missing,
 * which EwE reads as true), the map is in a projected coordinate system in
 * metres, which POSEIDON's lon/lat grids can't represent.</li>
 * <li>{@code DepthMap} is the depth of each cell, in metres, as a full map
 * string (see {@link EweMaps}). Land is 0.</li>
 * </ul>
 * Column names are matched case-insensitively. Column types are not prescribed:
 * any numeric column holds a number, and a boolean is either a boolean column
 * or a numeric one, where 0 is false and any other number is true (Access, for
 * example, stores true as -1).
 */
@Getter
public final class EcospaceBasemap {

  /**
   * The name of the EwE table that holds Ecospace base maps, one row per
   * Ecospace scenario.
   */
  public static final String TABLE_NAME = "EcospaceScenario";

  /**
   * The number of km in a degree, as EwE defines it
   * ({@code cEcoSpaceDataStructures}).
   */
  public static final double DEGREE_TO_KM = 40007.862917 / 360;

  private static final String SCENARIO_NAME = "ScenarioName";
  private static final String ROWS = "Inrow";
  private static final String COLS = "Incol";
  private static final String MIN_LAT = "MinLat";
  private static final String MIN_LON = "MinLon";
  private static final String CELL_LENGTH = "CellLength";
  private static final String ASSUME_SQUARE_CELLS = "AssumeSquareCells";
  private static final String DEPTH_MAP = "DepthMap";

  /** The number of rows of the grid. */
  private final int rows;

  /** The number of columns of the grid. */
  private final int cols;

  /**
   * The geographic bounding box of the grid, in degrees of longitude and
   * latitude.
   */
  @NonNull
  private final Envelope envelope;

  @Getter(lombok.AccessLevel.NONE)
  private final double[][] depth;

  /**
   * @param rows     the number of rows of the grid
   * @param cols     the number of columns of the grid
   * @param envelope the geographic bounding box of the grid
   * @param depth    the depth of each cell, in metres, indexed
   *                 {@code [row][col]}; it is copied
   * @throws IllegalArgumentException if the dimensions are not positive or
   *                                  {@code depth} is not {@code rows x cols}
   */
  public EcospaceBasemap(
    final int rows,
    final int cols,
    @NonNull final Envelope envelope,
    final double @NonNull [][] depth
  ) {
    checkArgument(
      rows > 0 && cols > 0,
      "The grid needs at least one row and one column"
    );
    checkArgument(
      depth.length == rows &&
        Arrays.stream(depth).allMatch(row -> row.length == cols),
      "The depth map is not %s x %s",
      rows,
      cols
    );
    this.rows = rows;
    this.cols = cols;
    this.envelope = envelope;
    this.depth = Arrays.stream(depth)
      .map(double[]::clone)
      .toArray(double[][]::new);
  }

  /**
   * Reads the base map of an Ecospace scenario from a table with the columns of
   * EwE's {@code EcospaceScenario} table (see the class documentation).
   *
   * @param table                a table with EwE's {@code EcospaceScenario}
   *                             columns
   * @param ecospaceScenarioName the name of the Ecospace scenario, in the
   *                             {@code ScenarioName} column
   * @return the scenario's base map
   * @throws IllegalArgumentException if no row or more than one row has that
   *                                  scenario name, a column is missing or has
   *                                  the wrong kind of values, the dimensions
   *                                  or cell length are not positive,
   *                                  {@code AssumeSquareCells} is true, or the
   *                                  depth map doesn't match the dimensions
   */
  public static EcospaceBasemap fromTable(
    @NonNull final Table table,
    @NonNull final String ecospaceScenarioName
  ) {
    final Column<?> names = column(table, SCENARIO_NAME);
    final List<Integer> matches = IntStream.range(0, table.rowCount())
      .filter(i -> ecospaceScenarioName.equals(names.getString(i)))
      .boxed()
      .toList();
    checkArgument(
      !matches.isEmpty(),
      "No Ecospace scenario named \"%s\" in table %s; available: %s",
      ecospaceScenarioName,
      table.name(),
      names.asList()
    );
    checkArgument(
      matches.size() == 1,
      "More than one Ecospace scenario named \"%s\" in table %s",
      ecospaceScenarioName,
      table.name()
    );
    final int row = matches.getFirst();

    checkArgument(
      !booleanValue(table, ASSUME_SQUARE_CELLS, row, true),
      "Ecospace scenario \"%s\" has %s set, so its map is in projected metres; " +
        "only maps in degrees of longitude and latitude are supported",
      ecospaceScenarioName,
      ASSUME_SQUARE_CELLS
    );
    final int rows = wholeNumber(table, ROWS, row);
    final int cols = wholeNumber(table, COLS, row);
    final double cellLength = number(table, CELL_LENGTH, row);
    checkArgument(
      cellLength > 0 && Double.isFinite(cellLength),
      "%s must be a positive number, but is %s",
      CELL_LENGTH,
      cellLength
    );
    final double cellSize = cellLength / DEGREE_TO_KM;
    final double north = number(table, MIN_LAT, row, 0);
    final double west = number(table, MIN_LON, row, 0);
    final Envelope envelope = new Envelope(
      west,
      west + cols * cellSize,
      north - rows * cellSize,
      north
    );
    final String depthMap = column(table, DEPTH_MAP).getString(row);
    return new EcospaceBasemap(
      rows,
      cols,
      envelope,
      EweMaps.decodeFullMap(depthMap, rows, cols)
    );
  }

  private static Column<?> column(final Table table, final String name) {
    checkArgument(
      table.columnNames().stream().anyMatch(name::equalsIgnoreCase),
      "Table %s has no %s column",
      table.name(),
      name
    );
    return table.column(name);
  }

  private static double number(
    final Table table,
    final String name,
    final int row
  ) {
    final Column<?> column = column(table, name);
    checkArgument(!column.isMissing(row), "No value in column %s", name);
    return number(column, row);
  }

  private static double number(
    final Table table,
    final String name,
    final int row,
    final double defaultValue
  ) {
    final Column<?> column = column(table, name);
    return column.isMissing(row) ? defaultValue : number(column, row);
  }

  private static double number(final Column<?> column, final int row) {
    if (column instanceof final NumericColumn<?> numberColumn) {
      return numberColumn.getDouble(row);
    }
    throw new IllegalArgumentException(
      "Column " + column.name() + " is not numeric"
    );
  }

  private static int wholeNumber(
    final Table table,
    final String name,
    final int row
  ) {
    final double value = number(table, name, row);
    checkArgument(
      value > 0 && value <= Integer.MAX_VALUE && value == Math.rint(value),
      "%s must be a positive whole number, but is %s",
      name,
      value
    );
    return (int) value;
  }

  private static boolean booleanValue(
    final Table table,
    final String name,
    final int row,
    final boolean defaultValue
  ) {
    final Column<?> column = column(table, name);
    if (column.isMissing(row)) {
      return defaultValue;
    }
    return switch (column) {
      case final BooleanColumn booleanColumn -> booleanColumn.get(row);
      case final NumericColumn<?> numberColumn -> numberColumn.getDouble(row) !=
        0;
      default -> throw new IllegalArgumentException(
        "Column " + name + " is neither boolean nor numeric"
      );
    };
  }

  /**
   * @param row the row, 0 being the northernmost
   * @param col the column, 0 being the westernmost
   * @return the depth of the cell, in metres; 0 is land
   */
  public double getDepth(final int row, final int col) {
    return depth[row][col];
  }
}
