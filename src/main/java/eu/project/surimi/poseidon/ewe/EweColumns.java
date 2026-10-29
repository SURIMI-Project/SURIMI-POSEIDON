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

import java.util.Optional;
import tech.tablesaw.api.BooleanColumn;
import tech.tablesaw.api.NumericColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

/**
 * Reads values from tables with the columns of an EwE table, wherever the
 * table comes from. Column names are matched case-insensitively. Column types
 * are not prescribed: any numeric column holds a number, and a boolean is
 * either a boolean column or a numeric one, where 0 is false and any other
 * number is true (Access, for example, stores true as -1).
 */
final class EweColumns {

  private EweColumns() {}

  static Column<?> column(final Table table, final String name) {
    checkArgument(
      table.columnNames().stream().anyMatch(name::equalsIgnoreCase),
      "Table %s has no %s column",
      table.name(),
      name
    );
    return table.column(name);
  }

  /**
   * @return the value as a string, or empty if the table has no such column or
   *         the value is missing
   */
  static Optional<String> optionalString(
    final Table table,
    final String name,
    final int row
  ) {
    if (table.columnNames().stream().noneMatch(name::equalsIgnoreCase)) {
      return Optional.empty();
    }
    final Column<?> column = table.column(name);
    return column.isMissing(row)
      ? Optional.empty()
      : Optional.of(column.getString(row));
  }

  static double number(final Table table, final String name, final int row) {
    final Column<?> column = column(table, name);
    checkArgument(!column.isMissing(row), "No value in column %s", name);
    return number(column, row);
  }

  static double number(
    final Table table,
    final String name,
    final int row,
    final double defaultValue
  ) {
    final Column<?> column = column(table, name);
    return column.isMissing(row) ? defaultValue : number(column, row);
  }

  static double number(final Column<?> column, final int row) {
    if (column instanceof final NumericColumn<?> numberColumn) {
      return numberColumn.getDouble(row);
    }
    throw new IllegalArgumentException(
      "Column " + column.name() + " is not numeric"
    );
  }

  static int wholeNumber(final Table table, final String name, final int row) {
    final double value = number(table, name, row);
    checkArgument(
      value > 0 && value <= Integer.MAX_VALUE && value == Math.rint(value),
      "%s must be a positive whole number, but is %s",
      name,
      value
    );
    return (int) value;
  }

  static boolean booleanValue(
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
}
