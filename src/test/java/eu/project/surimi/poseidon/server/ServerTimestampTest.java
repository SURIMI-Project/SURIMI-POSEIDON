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

package eu.project.surimi.poseidon.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ServerTimestampTest {

  private static final Timestamp WHOLE = Timestamp.newBuilder()
    .setSeconds(946_684_800)
    .build();
  private static final Timestamp FRACTIONAL = WHOLE.toBuilder()
    .setNanos(500_000_000)
    .build();

  @Test
  void toInstantRejectsFractionalSeconds() {
    assertThatThrownBy(() -> Server.toInstant(FRACTIONAL, "end_date_time"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("end_date_time")
      .hasMessageContaining("2000-01-01T00:00:00.500Z")
      .hasMessageContaining("whole seconds");
  }

  @Test
  void toLocalDateTimeRejectsFractionalSeconds() {
    assertThatThrownBy(() ->
      Server.toLocalDateTime(FRACTIONAL, "start_date_time")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("start_date_time")
      .hasMessageContaining("2000-01-01T00:00:00.500Z")
      .hasMessageContaining("whole seconds");
  }

  @Test
  void convertsWholeSecondTimestamps() {
    assertThat(Server.toInstant(WHOLE, "date_time")).isEqualTo(
      Instant.parse("2000-01-01T00:00:00Z")
    );
    assertThat(Server.toLocalDateTime(WHOLE, "date_time")).isEqualTo(
      LocalDateTime.of(2000, 1, 1, 0, 0)
    );
  }
}
