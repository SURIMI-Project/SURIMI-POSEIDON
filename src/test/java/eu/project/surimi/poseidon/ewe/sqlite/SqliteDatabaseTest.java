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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.project.surimi.poseidon.ewe.EweDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

class SqliteDatabaseTest {

  // EwE's own declared types (INTEGER, REAL, varchar, TEXT, DateTime), and a
  // few others that SQLite's affinity rules map
  private static final String[] MODEL = {
    """
    CREATE TABLE `Mixed` (
      `ID` INTEGER, `Small` TINYINT, `Value` REAL, `Single` FLOAT,
      `Flag` INTEGER, `Label` varchar, `Text` TEXT, `When` DateTime,
      `Untyped`, `Number` NUMERIC, PRIMARY KEY (`ID`)
    )
    """,
    """
    INSERT INTO `Mixed` VALUES
      (1, 7, 6.60383E-23, 1.5, 1, 'a, b; c', 'gzip:H4sI', '2026-10-28 00:00:00',
       'x', 'y'),
      (2, NULL, 2, NULL, 0, 'plain', NULL, NULL, NULL, NULL)
    """,
    "CREATE TABLE `Broken` (`ID` INTEGER)",
    "INSERT INTO `Broken` VALUES ('not a number')"
  };

  @TempDir
  Path tempDir;

  private EweDatabase database(final String... statements) throws SQLException {
    final Path path = tempDir.resolve("model.sqlite");
    try (
      final Connection connection = DriverManager.getConnection(
        "jdbc:sqlite:" + path
      );
      final Statement statement = connection.createStatement()
    ) {
      for (final String sql : statements) {
        statement.execute(sql);
      }
    }
    return EweDatabase.open(path);
  }

  @Test
  void opensSqliteFiles() throws SQLException {
    assertThat(database(MODEL)).isInstanceOf(SqliteDatabase.class);
  }

  @Test
  void typesColumnsFromTheirDeclaredTypes() throws SQLException {
    final Table table = database(MODEL).table("Mixed");
    assertThat(table.columnNames()).containsExactly(
      "ID",
      "Small",
      "Value",
      "Single",
      "Flag",
      "Label",
      "Text",
      "When",
      "Untyped",
      "Number"
    );
    assertThat(table.column("ID")).isInstanceOf(LongColumn.class);
    assertThat(table.column("Small")).isInstanceOf(LongColumn.class);
    assertThat(table.column("Value")).isInstanceOf(DoubleColumn.class);
    assertThat(table.column("Single")).isInstanceOf(DoubleColumn.class);
    assertThat(table.column("Flag")).isInstanceOf(LongColumn.class);
    assertThat(table.column("Label")).isInstanceOf(StringColumn.class);
    assertThat(table.column("Text")).isInstanceOf(StringColumn.class);
    assertThat(table.column("When")).isInstanceOf(StringColumn.class);
    assertThat(table.column("Untyped")).isInstanceOf(StringColumn.class);
    assertThat(table.column("Number")).isInstanceOf(StringColumn.class);
  }

  @Test
  void readsValuesAndNullsAsMissingValues() throws SQLException {
    final Table table = database(MODEL).table("Mixed");
    assertThat(table.rowCount()).isEqualTo(2);
    assertThat(table.longColumn("ID").asList()).containsExactly(1L, 2L);
    assertThat(table.longColumn("Small").get(0)).isEqualTo(7L);
    assertThat(table.doubleColumn("Value").get(0)).isEqualTo(6.60383E-23);
    assertThat(table.doubleColumn("Single").get(0)).isEqualTo(1.5);
    assertThat(table.longColumn("Flag").asList()).containsExactly(1L, 0L);
    assertThat(table.stringColumn("Label").get(0)).isEqualTo("a, b; c");
    assertThat(table.stringColumn("Text").get(0)).isEqualTo("gzip:H4sI");
    assertThat(table.stringColumn("When").get(0)).isEqualTo(
      "2026-10-28 00:00:00"
    );
    for (final String column : new String[] {
      "Small",
      "Single",
      "Text",
      "When",
      "Untyped",
      "Number"
    }) {
      assertThat(table.column(column).isMissing(1)).as(column).isTrue();
    }
  }

  @Test
  void widensIntegersInRealColumns() throws SQLException {
    final Table table = database(MODEL).table("Mixed");
    assertThat(table.doubleColumn("Value").get(1)).isEqualTo(2.0);
  }

  @Test
  void matchesTableAndColumnNamesCaseInsensitively() throws SQLException {
    final Table table = database(MODEL).table("mIXED");
    assertThat(table.name()).isEqualTo("Mixed");
    assertThat(table.column("label").name()).isEqualTo("Label");
  }

  @Test
  void readsOnlyTheRequestedTable() throws SQLException {
    final EweDatabase database = database(MODEL);
    assertThat(database.table("Mixed").rowCount()).isEqualTo(2);
    assertThatThrownBy(() -> database.table("Broken"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not a number")
      .hasMessageContaining("Broken");
  }

  @Test
  void rejectsUnknownTables() throws SQLException {
    final EweDatabase database = database(MODEL);
    assertThatThrownBy(() -> database.table("Missing"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No table Missing");
  }

  @Test
  void rejectsRealNumbersInIntegerColumns() throws SQLException {
    final EweDatabase database = database(
      "CREATE TABLE T (A INTEGER)",
      "INSERT INTO T VALUES (1.5)"
    );
    assertThatThrownBy(() -> database.table("T"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("stored as real")
      .hasMessageContaining("column A")
      .hasMessageContaining("row 1");
  }

  @Test
  void rejectsTextInRealColumns() throws SQLException {
    final EweDatabase database = database(
      "CREATE TABLE T (A REAL)",
      "INSERT INTO T VALUES ('x')"
    );
    assertThatThrownBy(() -> database.table("T"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("stored as text");
  }

  @Test
  void rejectsNumbersInTextColumns() throws SQLException {
    // A NUMERIC column stores a well-formed number as a number
    final EweDatabase database = database(
      "CREATE TABLE T (A NUMERIC)",
      "INSERT INTO T VALUES ('12')"
    );
    assertThatThrownBy(() -> database.table("T"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("stored as integer");
  }

  @Test
  void rejectsFilesThatAreNotSqliteDatabases() throws IOException {
    final Path path = Files.writeString(
      tempDir.resolve("model.sqlite"),
      "not a database"
    );
    final EweDatabase database = EweDatabase.open(path);
    assertThatThrownBy(() -> database.table("T"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Cannot read table T from SQLite file");
  }

  @Test
  void leavesTheFileUnchanged() throws SQLException, IOException {
    final EweDatabase database = database(MODEL);
    final Path path = tempDir.resolve("model.sqlite");
    final byte[] before = Files.readAllBytes(path);
    database.table("Mixed");
    assertThat(Files.readAllBytes(path)).isEqualTo(before);
    assertThat(tempDir.toFile().list()).containsExactly("model.sqlite");
  }
}
