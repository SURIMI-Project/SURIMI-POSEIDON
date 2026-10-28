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

import static eu.project.surimi.poseidon.ewe.EweMaps.decodeFullMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class EweMapsTest {

  private static final String MAP = "0,0,3;4,5.5,490;";
  private static final double[][] VALUES = { { 0, 0, 3 }, { 4, 5.5, 490 } };

  private static String gzip(final String map) throws IOException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (final GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
      gzip.write(map.getBytes(StandardCharsets.UTF_8));
    }
    return (
      EweMaps.GZIP_PREFIX +
      Base64.getEncoder().encodeToString(bytes.toByteArray())
    );
  }

  @Test
  void decodesAFullMapByRowThenColumn() {
    assertThat(decodeFullMap(MAP, 2, 3)).isDeepEqualTo(VALUES);
  }

  @Test
  void decodesACompressedMap() throws IOException {
    assertThat(decodeFullMap(gzip(MAP), 2, 3)).isDeepEqualTo(VALUES);
  }

  @Test
  void ignoresDoubleQuotes() {
    assertThat(decodeFullMap("\"" + MAP + "\"", 2, 3)).isDeepEqualTo(VALUES);
  }

  @Test
  void rejectsEmptyValues() {
    assertThatThrownBy(() -> decodeFullMap("0,,3;4,5.5,490;", 2, 3))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No value in row 0, column 1");
  }

  @Test
  void rejectsValuesThatAreNotNumbers() {
    assertThatThrownBy(() -> decodeFullMap("0,x,3;4,5.5,490;", 2, 3))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Not a number");
  }

  @Test
  void rejectsTheWrongNumberOfRows() {
    assertThatThrownBy(() -> decodeFullMap(MAP, 3, 3)).isInstanceOf(
      IllegalArgumentException.class
    );
    assertThatThrownBy(() -> decodeFullMap(MAP, 1, 3)).isInstanceOf(
      IllegalArgumentException.class
    );
    assertThatThrownBy(() ->
      decodeFullMap("0,0,3;4,5.5,490", 2, 3)
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsTheWrongNumberOfColumns() {
    assertThatThrownBy(() -> decodeFullMap(MAP, 2, 2))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Expected 2 values in row 0, but found 3");
    assertThatThrownBy(() ->
      decodeFullMap("0,0,3,;4,5.5,490;", 2, 3)
    ).isInstanceOf(IllegalArgumentException.class);
  }
}
