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

package eu.project.surimi.poseidon.ewe.eiixml;

import static eu.project.surimi.poseidon.ewe.eiixml.EiiXmlDatabase.splitRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.project.surimi.poseidon.ewe.EweDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.tablesaw.api.BooleanColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

class EiiXmlDatabaseTest {

  private static final String MODEL = """
  <?xml version="1.0"?>
  <EwEModel Name="Test" DBVersion="6.702" EwEVersion="6.7.0.0">
    <Table Name="Mixed" Columns="ID:System.Int64,Small:System.Byte,Value:System.Single,Flag:System.Boolean,Label:System.String,When:System.DateTime" Num="3">
      <Row>1,7,6.60383E-23,True,"a, b; c",2026-10-28</Row>
      <Row>2,,1.5,False,plain</Row>
      <Row>  </Row>
      <Row>3,,,,</Row>
    </Table>
    <Table Name="Broken" Columns="ID:System.Int64" Num="1">
      <Row>not a number</Row>
    </Table>
  </EwEModel>
  """;

  @TempDir
  Path tempDir;

  private EweDatabase database(final String content) throws IOException {
    final Path path = tempDir.resolve("model.eiixml");
    Files.writeString(path, content);
    return EweDatabase.open(path);
  }

  @Test
  void splitsRowsAsEweDoes() {
    assertThat(splitRow("a,b")).containsExactly("a", "b");
    assertThat(splitRow("\"x,y;z\",b")).containsExactly("x,y;z", "b");
    assertThat(splitRow("a,,b")).containsExactly("a", "", "b");
    assertThat(splitRow("a,,")).containsExactly("a", "");
    assertThat(splitRow("")).containsExactly("");
  }

  @Test
  void typesColumnsFromTheirDeclarations() throws IOException {
    final Table table = database(MODEL).table("Mixed");
    assertThat(table.columnNames()).containsExactly(
      "ID",
      "Small",
      "Value",
      "Flag",
      "Label",
      "When"
    );
    assertThat(table.column("ID")).isInstanceOf(LongColumn.class);
    assertThat(table.column("Small")).isInstanceOf(LongColumn.class);
    assertThat(table.column("Value")).isInstanceOf(DoubleColumn.class);
    assertThat(table.column("Flag")).isInstanceOf(BooleanColumn.class);
    assertThat(table.column("Label")).isInstanceOf(StringColumn.class);
    assertThat(table.column("When")).isInstanceOf(StringColumn.class);
  }

  @Test
  void readsValuesSkipsBlankRowsAndFillsDroppedFieldsWithMissingValues()
    throws IOException {
    final Table table = database(MODEL).table("Mixed");
    assertThat(table.rowCount()).isEqualTo(3);
    assertThat(table.longColumn("ID").asList()).containsExactly(1L, 2L, 3L);
    assertThat(table.longColumn("Small").get(0)).isEqualTo(7L);
    assertThat(table.doubleColumn("Value").get(0)).isEqualTo(6.60383E-23);
    assertThat(table.booleanColumn("Flag").get(0)).isTrue();
    assertThat(table.booleanColumn("Flag").get(1)).isFalse();
    assertThat(table.stringColumn("Label").get(0)).isEqualTo("a, b; c");
    assertThat(table.stringColumn("When").get(0)).isEqualTo("2026-10-28");
    assertThat(table.column("Small").isMissing(1)).isTrue();
    assertThat(table.column("When").isMissing(1)).isTrue();
    for (final String column : new String[] {
      "Small",
      "Value",
      "Flag",
      "Label",
      "When"
    }) {
      assertThat(table.column(column).isMissing(2)).as(column).isTrue();
    }
  }

  @Test
  void matchesTableAndColumnNamesCaseInsensitively() throws IOException {
    final Table table = database(MODEL).table("mIXED");
    assertThat(table.column("label").name()).isEqualTo("Label");
  }

  @Test
  void readsOnlyTheRequestedTable() throws IOException {
    final EweDatabase database = database(MODEL);
    assertThat(database.table("Mixed").rowCount()).isEqualTo(3);
    assertThatThrownBy(() -> database.table("Broken"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not a number")
      .hasMessageContaining("Broken");
  }

  @Test
  void rejectsUnknownTables() throws IOException {
    final EweDatabase database = database(MODEL);
    assertThatThrownBy(() -> database.table("Missing"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No table Missing");
  }

  @Test
  void rejectsRowsWithMoreValuesThanColumns() throws IOException {
    final EweDatabase database = database(
      """
      <EwEModel>
        <Table Name="T" Columns="A:System.Int64"><Row>1,2</Row></Table>
      </EwEModel>
      """
    );
    assertThatThrownBy(() -> database.table("T"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("2 values for 1 columns");
  }

  @Test
  void rejectsValuesThatAreNotBooleans() throws IOException {
    final EweDatabase database = database(
      """
      <EwEModel>
        <Table Name="T" Columns="A:System.Boolean"><Row>yes</Row></Table>
      </EwEModel>
      """
    );
    assertThatThrownBy(() -> database.table("T"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("yes");
  }

  @Test
  void doesNotExpandEntitiesDeclaredInADtd() throws IOException {
    final EweDatabase database = database(
      """
      <?xml version="1.0"?>
      <!DOCTYPE EwEModel [<!ENTITY secret "expanded">]>
      <EwEModel>
        <Table Name="T" Columns="A:System.String"><Row>&secret;</Row></Table>
      </EwEModel>
      """
    );
    assertThatThrownBy(() -> database.table("T"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Malformed");
  }

  @Test
  void openRejectsMissingFilesAndUnknownFormats() throws IOException {
    assertThatThrownBy(() ->
      EweDatabase.open(tempDir.resolve("missing.eiixml"))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No EwE database file");
    final Path other = Files.writeString(tempDir.resolve("model.ewemdb"), "");
    assertThatThrownBy(() -> EweDatabase.open(other))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unsupported EwE database format");
  }
}
