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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;

/**
 * Decodes EwE map strings: the values of a spatial layer (such as
 * {@code DepthMap} in the {@code EcospaceScenario} table) written as one string
 * by EwE's {@code cStringUtils.ArrayToString} and read back by
 * {@code cStringUtils.StringToArray}. This is EwE's value encoding, the same in
 * every file format.
 * <p>
 * A map string may be compressed, as {@code gzip:} followed by the base64
 * encoding of the gzipped string. Once decompressed, every row ends with
 * {@code ;}, so a map of {@code rows} rows has {@code rows + 1} segments, the
 * last one empty, and within a row the cells are separated by {@code ,}. Double
 * quotes are ignored, as in EwE.
 * <p>
 * Only full maps (one value per cell) are handled. EwE also writes masked maps,
 * which hold only the water cells of each row; reading those needs the depth
 * map, and is not supported yet. Boolean maps, such as a fleet's
 * {@code PortMap}, are full maps of {@code 0} and {@code 1}, but EwE writes a
 * row of a boolean map only if it holds at least one true cell, so an empty
 * row is all false. Decoding is strict: where EwE silently keeps default
 * values for missing or unreadable cells, this throws.
 */
public final class EweMaps {

  /** The prefix EwE puts before a compressed map string. */
  public static final String GZIP_PREFIX = "gzip:";

  private EweMaps() {}

  /**
   * @param map  the map string, compressed or not
   * @param rows the number of rows of the map, EwE's {@code Inrow}
   * @param cols the number of columns of the map, EwE's {@code Incol}
   * @return the map's values, indexed {@code [row][col]}, row 0 being the
   *         northernmost
   * @throws IllegalArgumentException if the string doesn't hold exactly
   *                                  {@code rows} rows of {@code cols} numbers
   *                                  each
   */
  public static double[][] decodeFullMap(
    final String map,
    final int rows,
    final int cols
  ) {
    final String[] segments = segments(map, rows, cols);
    return IntStream.range(0, rows)
      .mapToObj(row -> {
        final String[] tokens = tokens(segments[row], row, cols);
        return IntStream.range(0, cols)
          .mapToDouble(col -> parse(tokens[col], row, col))
          .toArray();
      })
      .toArray(double[][]::new);
  }

  /**
   * @param map  the map string, compressed or not
   * @param rows the number of rows of the map, EwE's {@code Inrow}
   * @param cols the number of columns of the map, EwE's {@code Incol}
   * @return the map's values, indexed {@code [row][col]}, row 0 being the
   *         northernmost; a row that is empty in the string is all false
   * @throws IllegalArgumentException if the string doesn't hold exactly
   *                                  {@code rows} rows, each empty or of
   *                                  {@code cols} values that are {@code 0}
   *                                  or {@code 1}
   */
  public static boolean[][] decodeBooleanMap(
    final String map,
    final int rows,
    final int cols
  ) {
    final String[] segments = segments(map, rows, cols);
    final boolean[][] values = new boolean[rows][cols];
    for (int row = 0; row < rows; row++) {
      if (segments[row].isEmpty()) {
        continue;
      }
      final String[] tokens = tokens(segments[row], row, cols);
      for (int col = 0; col < cols; col++) {
        values[row][col] = parseBoolean(tokens[col], row, col);
      }
    }
    return values;
  }

  private static String[] segments(
    final String map,
    final int rows,
    final int cols
  ) {
    checkArgument(
      rows > 0 && cols > 0,
      "A map needs at least one row and one column"
    );
    final String[] segments = decompress(map).replace("\"", "").split(";", -1);
    checkArgument(
      segments.length == rows + 1 && segments[rows].isEmpty(),
      "Expected %s rows, each ending with ';', but found %s segments",
      rows,
      segments.length
    );
    return segments;
  }

  private static String[] tokens(
    final String segment,
    final int row,
    final int cols
  ) {
    final String[] tokens = segment.split(",", -1);
    checkArgument(
      tokens.length == cols,
      "Expected %s values in row %s, but found %s",
      cols,
      row,
      tokens.length
    );
    return tokens;
  }

  private static boolean parseBoolean(
    final String token,
    final int row,
    final int col
  ) {
    return switch (token.strip()) {
      case "0" -> false;
      case "1" -> true;
      default -> throw new IllegalArgumentException(
        "Not 0 or 1 in row " + row + ", column " + col + ": " + token
      );
    };
  }

  private static double parse(
    final String token,
    final int row,
    final int col
  ) {
    checkArgument(!token.isBlank(), "No value in row %s, column %s", row, col);
    try {
      return Double.parseDouble(token.strip());
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
        "Not a number in row " + row + ", column " + col + ": " + token,
        e
      );
    }
  }

  /**
   * @param map a map string, compressed or not
   * @return the map string, decompressed if it was compressed, as EwE's
   * {@code cStringUtils.Decompress} does
   */
  static String decompress(final String map) {
    return map.startsWith(GZIP_PREFIX) ? gunzipped(map) : map;
  }

  private static String gunzipped(final String map) {
    final byte[] compressed = Base64.getMimeDecoder().decode(
      map.substring(GZIP_PREFIX.length())
    );
    try (
      final InputStream inputStream = new GZIPInputStream(
        new ByteArrayInputStream(compressed)
      )
    ) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Cannot decompress map string", e);
    }
  }
}
