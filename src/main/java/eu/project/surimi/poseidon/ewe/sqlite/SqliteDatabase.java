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

package eu.project.surimi.poseidon.ewe.sqlite;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu.project.surimi.poseidon.ewe.EweDatabase;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.sqlite.SQLiteConfig;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

/**
 * An {@link EweDatabase} stored in EwE's SQLite format ({@code .sqlite}). This
 * is the only class that knows the format.
 * <p>
 * The file is a SQLite database with one SQL table per EwE table. SQLite does
 * not enforce column types: a column has a declared type, and each value its
 * own storage class ({@code integer}, {@code real}, {@code text},
 * {@code blob} or {@code null}). A column's Tablesaw type comes from its
 * declared type, by SQLite's type affinity rules:
 * <ul>
 * <li>a declared type containing {@code INT} gives a {@link LongColumn};</li>
 * <li>one containing {@code CHAR}, {@code CLOB} or {@code TEXT} (so
 * {@code varchar}) a {@link StringColumn};</li>
 * <li>one containing {@code BLOB}, or no declared type, a
 * {@link StringColumn};</li>
 * <li>one containing {@code REAL}, {@code FLOA} or {@code DOUB} a
 * {@link DoubleColumn};</li>
 * <li>any other (such as EwE's {@code DateTime}) a {@link StringColumn}, as the
 * {@code .eiixml} reader does for non-numeric types.</li>
 * </ul>
 * EwE stores booleans as integers, 0 or 1. A null is a missing value. An
 * integer in a real-number column is widened to a double. Any other value
 * whose storage class doesn't fit its column, such as text in a numeric column
 * or a real number in a whole-number one, is an error.
 * <p>
 * {@link #table(String)} opens a read-only connection to the file and reads
 * only the requested table, with its rows in the order SQLite returns them.
 */
@RequiredArgsConstructor
public final class SqliteDatabase implements EweDatabase {

  private static final String FIND_TABLE =
    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ? " +
    "COLLATE NOCASE";
  private static final String LIST_COLUMNS =
    "SELECT name, type FROM pragma_table_info(?) ORDER BY cid";
  private static final String JDBC_URL_PREFIX = "jdbc:sqlite:";
  private static final String QUOTE = "\"";

  /** The {@code .sqlite} file this database reads from. */
  @Getter
  @NonNull
  private final Path path;

  /** Quotes an SQL identifier, doubling any double quote it contains. */
  private static String quote(final String identifier) {
    return QUOTE + identifier.replace(QUOTE, QUOTE + QUOTE) + QUOTE;
  }

  private Connection connect() throws SQLException {
    final SQLiteConfig config = new SQLiteConfig();
    config.setReadOnly(true);
    return config.createConnection(JDBC_URL_PREFIX + path.toAbsolutePath());
  }

  @Override
  public Table table(final String name) {
    try (final Connection connection = connect()) {
      final String tableName = findTable(connection, name);
      return readTable(
        connection,
        tableName,
        readDeclarations(connection, tableName)
      );
    } catch (final SQLException e) {
      throw new IllegalArgumentException(
        "Cannot read table " + name + " from SQLite file " + path,
        e
      );
    }
  }

  /**
   * @return the name of the table matching {@code name} case-insensitively, as
   *         the file spells it
   */
  private String findTable(final Connection connection, final String name)
    throws SQLException {
    try (
      final PreparedStatement statement = connection.prepareStatement(
        FIND_TABLE
      )
    ) {
      statement.setString(1, name);
      try (final ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalArgumentException(
            "No table " + name + " in " + path
          );
        }
        return resultSet.getString(1);
      }
    }
  }

  private static ImmutableList<ColumnDeclaration> readDeclarations(
    final Connection connection,
    final String tableName
  ) throws SQLException {
    try (
      final PreparedStatement statement = connection.prepareStatement(
        LIST_COLUMNS
      )
    ) {
      statement.setString(1, tableName);
      try (final ResultSet resultSet = statement.executeQuery()) {
        final ImmutableList.Builder<ColumnDeclaration> declarations =
          ImmutableList.builder();
        while (resultSet.next()) {
          final String declaredType = resultSet.getString(2);
          declarations.add(
            new ColumnDeclaration(
              resultSet.getString(1),
              declaredType,
              ColumnType.of(declaredType)
            )
          );
        }
        return declarations.build();
      }
    }
  }

  /**
   * Reads every row of the table, selecting each column's storage class next
   * to its value, so that a value is read according to how SQLite stores it.
   */
  @SuppressFBWarnings(
    value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
    justification = "The table and column names come from sqlite_master and " +
      "pragma_table_info, and are quoted"
  )
  private Table readTable(
    final Connection connection,
    final String tableName,
    final List<ColumnDeclaration> declarations
  ) throws SQLException {
    final List<Column<?>> columns = declarations
      .stream()
      .<Column<?>>map(declaration ->
        declaration.type().newColumn(declaration.name())
      )
      .toList();
    final String query =
      "SELECT " +
      declarations
        .stream()
        .map(declaration -> quote(declaration.name()))
        .map(column -> "typeof(" + column + "), " + column)
        .collect(joining(", ")) +
      " FROM " +
      quote(tableName);
    try (
      final Statement statement = connection.createStatement();
      final ResultSet resultSet = statement.executeQuery(query)
    ) {
      int row = 0;
      while (resultSet.next()) {
        row++;
        for (int col = 0; col < declarations.size(); col++) {
          final ColumnDeclaration declaration = declarations.get(col);
          final String storageClass = resultSet.getString(2 * col + 1);
          try {
            columns
              .get(col)
              .appendObj(
                declaration.type().read(resultSet, 2 * col + 2, storageClass)
              );
          } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
              "Cannot read value " +
                resultSet.getString(2 * col + 2) +
                " (stored as " +
                storageClass +
                ") of column " +
                declaration.name() +
                " (declared " +
                declaration.declaredType() +
                ") in row " +
                row +
                " of table " +
                tableName +
                " in " +
                path,
              e
            );
          }
        }
      }
    }
    return Table.create(tableName, columns);
  }

  /** A column's name and declared type, from {@code pragma_table_info}. */
  private record ColumnDeclaration(
    String name,
    String declaredType,
    ColumnType type
  ) {}

  /**
   * The Tablesaw column type of a SQLite column, and how to read its values.
   */
  @RequiredArgsConstructor
  private enum ColumnType {
    LONG(LongColumn::create) {
      @Override
      Object readStored(
        final ResultSet resultSet,
        final int index,
        final String storageClass
      ) throws SQLException {
        checkStorageClass(storageClass, INTEGER);
        return resultSet.getLong(index);
      }
    },
    DOUBLE(DoubleColumn::create) {
      @Override
      Object readStored(
        final ResultSet resultSet,
        final int index,
        final String storageClass
      ) throws SQLException {
        checkStorageClass(storageClass, INTEGER, REAL);
        return storageClass.equals(INTEGER)
          ? (double) resultSet.getLong(index)
          : resultSet.getDouble(index);
      }
    },
    STRING(StringColumn::create) {
      @Override
      Object readStored(
        final ResultSet resultSet,
        final int index,
        final String storageClass
      ) throws SQLException {
        checkStorageClass(storageClass, TEXT);
        return resultSet.getString(index);
      }
    };

    private static final String NULL = "null";
    private static final String INTEGER = "integer";
    private static final String REAL = "real";
    private static final String TEXT = "text";

    private final Function<String, Column<?>> columnConstructor;

    /**
     * @param declaredType the column's declared type, possibly empty
     * @return the column type that holds its values, by SQLite's type affinity
     *         rules
     */
    static ColumnType of(final String declaredType) {
      final String type = declaredType.toUpperCase(Locale.ROOT);
      // SQLite checks the text and blob names before the real-number ones
      final boolean isText = Stream.of("CHAR", "CLOB", "TEXT", "BLOB").anyMatch(
        type::contains
      );
      final boolean isReal = Stream.of("REAL", "FLOA", "DOUB").anyMatch(
        type::contains
      );
      final ColumnType columnType;
      if (type.contains("INT")) {
        columnType = LONG;
      } else if (isReal && !isText) {
        columnType = DOUBLE;
      } else {
        columnType = STRING;
      }
      return columnType;
    }

    private static void checkStorageClass(
      final String storageClass,
      final String... expected
    ) {
      if (!Arrays.asList(expected).contains(storageClass)) {
        throw new IllegalArgumentException(
          "Expected a value stored as " +
            String.join(" or ", expected) +
            ", not " +
            storageClass
        );
      }
    }

    Column<?> newColumn(final String name) {
      return columnConstructor.apply(name);
    }

    /**
     * @param resultSet    the result set, positioned on a row
     * @param index        the index of the value in the row
     * @param storageClass the value's storage class, as SQL's {@code typeof}
     *                     gives it
     * @return the value, or {@code null} if it is null
     * @throws IllegalArgumentException if the storage class doesn't fit this
     *                                  type
     */
    Object read(
      final ResultSet resultSet,
      final int index,
      final String storageClass
    ) throws SQLException {
      return storageClass.equals(NULL)
        ? null
        : readStored(resultSet, index, storageClass);
    }

    abstract Object readStored(
      ResultSet resultSet,
      int index,
      String storageClass
    ) throws SQLException;
  }
}
