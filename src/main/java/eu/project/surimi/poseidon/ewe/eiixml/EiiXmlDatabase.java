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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.function.Predicate.not;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import com.google.common.collect.ImmutableList;
import eu.project.surimi.poseidon.ewe.EweDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import tech.tablesaw.api.BooleanColumn;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

/**
 * An {@link EweDatabase} stored in EwE's XML export format ({@code .eiixml}),
 * mirroring EwE's reader ({@code cEIIXMLDataSource.ReadTable}) and writer
 * ({@code cEIIXMLDataSource.ToFieldValue}). This is the only class that knows
 * the format.
 * <p>
 * The file is an {@code <EwEModel>} element holding one {@code <Table>} element
 * per table. Each table declares its columns in a
 * {@code Columns="Name:System.Type,..."} attribute and holds one {@code <Row>}
 * element per row, whose text is the row's values separated by commas.
 * <ul>
 * <li>A value containing {@code ,} or {@code ;} is wrapped in double quotes.
 * The writer removes every double quote from string values, so quotes only ever
 * delimit values (see {@link #splitRow(String)}).</li>
 * <li>A null is an empty value, and a trailing empty value is dropped, so a row
 * can have fewer values than its table has columns. The missing ones are
 * null.</li>
 * <li>Numbers use {@code .} as the decimal separator, with no thousands
 * separator. Booleans are {@code True} or {@code False}.</li>
 * <li>Rows with only whitespace are skipped.</li>
 * </ul>
 * Whole-number columns ({@code Byte}, {@code Int16}, {@code Int32},
 * {@code Int64}) become {@link LongColumn}s, real-number columns
 * ({@code Single}, {@code Double}, {@code Decimal}) {@link DoubleColumn}s,
 * {@code Boolean} columns {@link BooleanColumn}s, and every other column a
 * {@link StringColumn}. A value that doesn't parse as its column's type is an
 * error, where EwE would silently leave it null.
 * <p>
 * {@link #table(String)} streams the file and builds only the requested table.
 * The XML parser reads no DTD and resolves no external entity.
 */
@RequiredArgsConstructor
public final class EiiXmlDatabase implements EweDatabase {

  private static final String TABLE_ELEMENT = "Table";
  private static final String ROW_ELEMENT = "Row";
  private static final String NAME_ATTRIBUTE = "Name";
  private static final String COLUMNS_ATTRIBUTE = "Columns";
  private static final char QUOTE = '"';
  private static final char SEPARATOR = ',';
  private static final char TYPE_SEPARATOR = ':';

  /** The {@code .eiixml} file this database reads from. */
  @Getter
  @NonNull
  private final Path path;

  /**
   * Splits an {@code .eiixml} row (or column declaration) into its values, as
   * EwE's {@code cStringUtils.SplitQualified} does: a double quote toggles
   * quoting, a comma outside quotes ends a value, a value's surrounding double
   * quotes are removed, and a trailing empty value is dropped. An empty row has
   * a single empty value.
   *
   * @param row the row text
   * @return the row's values, in order
   */
  static ImmutableList<String> splitRow(final String row) {
    if (row.isEmpty()) {
      return ImmutableList.of("");
    }
    final ImmutableList.Builder<String> values = ImmutableList.builder();
    boolean quoted = false;
    int start = 0;
    for (int i = 0; i < row.length(); i++) {
      final char c = row.charAt(i);
      if (c == QUOTE) {
        quoted = !quoted;
      } else if (!quoted && c == SEPARATOR) {
        values.add(unquote(row.substring(start, i)));
        start = i + 1;
      }
    }
    if (start < row.length()) {
      values.add(unquote(row.substring(start)));
    }
    return values.build();
  }

  /** Removes one leading and one trailing double quote, if there are any. */
  private static String unquote(final String value) {
    final int start = !value.isEmpty() && value.charAt(0) == QUOTE ? 1 : 0;
    final int end =
      value.length() > start && value.charAt(value.length() - 1) == QUOTE
        ? value.length() - 1
        : value.length();
    return value.substring(start, end);
  }

  private static XMLInputFactory newInputFactory() {
    final XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory;
  }

  /**
   * Reads the text of every row of the table whose start element {@code reader}
   * is positioned on, leaving the reader after the table's end element.
   */
  private static ImmutableList<String> readRowTexts(
    final XMLStreamReader reader
  ) throws XMLStreamException {
    final ImmutableList.Builder<String> rowTexts = ImmutableList.builder();
    while (reader.hasNext()) {
      final int event = reader.next();
      if (event == START_ELEMENT && reader.getLocalName().equals(ROW_ELEMENT)) {
        rowTexts.add(reader.getElementText());
      } else if (
        event == END_ELEMENT && reader.getLocalName().equals(TABLE_ELEMENT)
      ) {
        break;
      }
    }
    return rowTexts.build();
  }

  @Override
  public Table table(final String name) {
    try (final InputStream inputStream = Files.newInputStream(path)) {
      final XMLStreamReader reader = newInputFactory().createXMLStreamReader(
        inputStream
      );
      try {
        while (reader.hasNext()) {
          if (
            reader.next() == START_ELEMENT &&
            reader.getLocalName().equals(TABLE_ELEMENT) &&
            name.equalsIgnoreCase(
              reader.getAttributeValue(null, NAME_ATTRIBUTE)
            )
          ) {
            return readTable(reader);
          }
        }
      } finally {
        reader.close();
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final XMLStreamException e) {
      throw new IllegalArgumentException("Malformed .eiixml file " + path, e);
    }
    throw new IllegalArgumentException("No table " + name + " in " + path);
  }

  /** Reads the table whose start element {@code reader} is positioned on. */
  private Table readTable(final XMLStreamReader reader)
    throws XMLStreamException {
    final String tableName = reader.getAttributeValue(null, NAME_ATTRIBUTE);
    final String declarations = reader.getAttributeValue(
      null,
      COLUMNS_ATTRIBUTE
    );
    checkArgument(
      declarations != null,
      "Table %s in %s has no %s attribute",
      tableName,
      path,
      COLUMNS_ATTRIBUTE
    );
    final List<ColumnDeclaration> columns = splitRow(declarations)
      .stream()
      .map(declaration -> parseDeclaration(declaration, tableName))
      .toList();
    final List<List<String>> rows = readRowTexts(reader)
      .stream()
      .filter(not(String::isBlank))
      .<List<String>>map(EiiXmlDatabase::splitRow)
      .toList();
    IntStream.range(0, rows.size())
      .filter(row -> rows.get(row).size() > columns.size())
      .findFirst()
      .ifPresent(row -> {
        throw new IllegalArgumentException(
          "Row " +
            (row + 1) +
            " of table " +
            tableName +
            " in " +
            path +
            " has " +
            rows.get(row).size() +
            " values for " +
            columns.size() +
            " columns"
        );
      });
    return Table.create(
      tableName,
      IntStream.range(0, columns.size()).<Column<?>>mapToObj(col ->
        buildColumn(
          columns.get(col),
          rows
            .stream()
            .map(row -> col < row.size() ? row.get(col) : "")
            .toList(),
          tableName
        )
      )
    );
  }

  private ColumnDeclaration parseDeclaration(
    final String declaration,
    final String tableName
  ) {
    final int typeStart = declaration.lastIndexOf(TYPE_SEPARATOR);
    checkArgument(
      typeStart > 0,
      "Malformed column declaration %s in table %s of %s",
      declaration,
      tableName,
      path
    );
    return new ColumnDeclaration(
      declaration.substring(0, typeStart),
      ColumnType.of(declaration.substring(typeStart + 1))
    );
  }

  /**
   * @param declaration the column's declaration
   * @param values      the column's values, one per row, as written in the file
   * @param tableName   the table's name, for error messages
   * @return the column
   */
  private Column<?> buildColumn(
    final ColumnDeclaration declaration,
    final List<String> values,
    final String tableName
  ) {
    final Column<?> column = declaration.type().newColumn(declaration.name());
    IntStream.range(0, values.size()).forEach(row -> {
      try {
        column.appendObj(declaration.type().parse(values.get(row)));
      } catch (final IllegalArgumentException e) {
        throw new IllegalArgumentException(
          "Cannot read value \"" +
            values.get(row) +
            "\" of column " +
            declaration.name() +
            " in row " +
            (row + 1) +
            " of table " +
            tableName +
            " in " +
            path,
          e
        );
      }
    });
    return column;
  }

  /** A column's name and type, from the table's {@code Columns} attribute. */
  private record ColumnDeclaration(String name, ColumnType type) {}

  /**
   * The Tablesaw column type of an {@code .eiixml} column, and how to read its
   * values.
   */
  @RequiredArgsConstructor
  private enum ColumnType {
    LONG(LongColumn::create, Long::parseLong),
    DOUBLE(DoubleColumn::create, Double::parseDouble),
    BOOLEAN(BooleanColumn::create, ColumnType::parseBoolean),
    STRING(StringColumn::create, value -> value) {
      /** EwE keeps string values as they are, blank or not. */
      @Override
      Object parse(final String value) {
        return value;
      }
    };

    private final Function<String, Column<?>> columnConstructor;
    private final Function<String, Object> parser;

    /**
     * @param declaredType the .NET type of the column, such as
     *                     {@code System.Int32}
     * @return the column type that holds its values
     */
    static ColumnType of(final String declaredType) {
      return switch (declaredType) {
        case
          "System.Byte",
          "System.SByte",
          "System.Int16",
          "System.UInt16",
          "System.Int32",
          "System.UInt32",
          "System.Int64" -> LONG;
        case "System.Single", "System.Double", "System.Decimal" -> DOUBLE;
        case "System.Boolean" -> BOOLEAN;
        default -> STRING;
      };
    }

    private static Boolean parseBoolean(final String value) {
      if (value.equalsIgnoreCase("True")) {
        return true;
      }
      checkArgument(
        value.equalsIgnoreCase("False"),
        "Not a boolean: %s",
        value
      );
      return false;
    }

    Column<?> newColumn(final String name) {
      return columnConstructor.apply(name);
    }

    /**
     * @param value the value as written in the file
     * @return the value, or {@code null} if it is blank
     * @throws IllegalArgumentException if the value doesn't parse as this type
     */
    Object parse(final String value) {
      return value.isBlank() ? null : parser.apply(value.strip());
    }
  }
}
