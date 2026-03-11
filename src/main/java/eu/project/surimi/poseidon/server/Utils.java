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

import com.google.protobuf.Timestamp;
import uk.ac.ox.poseidon.core.Simulation;

import static eu.project.surimi.poseidon.server.Server.toLocalDateTime;
import static io.grpc.Status.INVALID_ARGUMENT;
import static java.time.temporal.ChronoUnit.SECONDS;

public class Utils {

    private Utils() {}

    public static void checkRequestDateTimeAlignment(
        final Timestamp requestTimestamp,
        final Simulation simulation
    ) {
        final var requestDateTime = toLocalDateTime(requestTimestamp);
        final var simulationDateTime = simulation.getTemporalSchedule().getDateTime();
        if (SECONDS.between(requestDateTime, simulationDateTime) > 1) {
            throw INVALID_ARGUMENT.withDescription(
                ("Request date-time %s is more than one second away from current simulation " +
                    "date-time %s.").formatted(requestDateTime, simulationDateTime)
            ).asRuntimeException();
        }
    }

}
