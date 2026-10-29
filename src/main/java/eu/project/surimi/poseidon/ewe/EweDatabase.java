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

import eu.project.surimi.poseidon.ewe.eiixml.EiiXmlDatabase;
import eu.project.surimi.poseidon.ewe.sqlite.SqliteDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import tech.tablesaw.api.Table;

/**
 * An Ecopath with Ecosim (EwE) model stored in one of EwE's file formats, seen
 * as a set of named tables. The tables follow EwE's own schema (for example the
 * {@code EcospaceScenario} table, with its {@code DepthMap} column), which EwE
 * keeps the same across its file formats; only the implementations of this
 * interface know how a format stores them.
 * <p>
 * Opening a database reads no table: each call to {@link #table(String)} reads
 * the requested table from the file. Cache tables by reading them through
 * {@link Factories#eweTable(uk.ac.ox.poseidon.core.Factory, String)}, not by
 * calling this repeatedly.
 */
public interface EweDatabase {
  /**
   * The file extension of EwE's XML export format, handled by
   * {@link EiiXmlDatabase}.
   */
  String EIIXML_EXTENSION = ".eiixml";

  /**
   * The file extension of EwE's SQLite format, handled by
   * {@link SqliteDatabase}.
   */
  String SQLITE_EXTENSION = ".sqlite";

  /**
   * Opens the EwE database stored at {@code path}, choosing the format from the
   * file extension. This is the only place that maps a file to a format.
   *
   * @param path the database file
   * @return the database, with no table read yet
   * @throws IllegalArgumentException if the file does not exist or its format
   *                                  is not supported
   */
  static EweDatabase open(final Path path) {
    checkArgument(
      Files.isRegularFile(path),
      "No EwE database file at %s",
      path
    );
    final String lowerCasePath = path.toString().toLowerCase(Locale.ROOT);
    final boolean isEiiXml = lowerCasePath.endsWith(EIIXML_EXTENSION);
    checkArgument(
      isEiiXml || lowerCasePath.endsWith(SQLITE_EXTENSION),
      "Unsupported EwE database format: %s (supported: %s, %s)",
      path,
      EIIXML_EXTENSION,
      SQLITE_EXTENSION
    );
    return isEiiXml ? new EiiXmlDatabase(path) : new SqliteDatabase(path);
  }

  /**
   * Reads one table. Table names are matched case-insensitively, as EwE does,
   * and so are column names (Tablesaw's own rule). Column types are the
   * implementation's choice: code reading the table must accept any numeric
   * column type for numbers. Null values are Tablesaw's missing values.
   *
   * @param name the EwE table name, e.g. {@code EcospaceScenario}
   * @return a new table holding the table's rows
   * @throws IllegalArgumentException if the database has no table with that
   *                                  name
   */
  Table table(String name);
}
