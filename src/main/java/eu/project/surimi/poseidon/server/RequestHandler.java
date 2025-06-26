/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025, University of Oxford.
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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static io.grpc.Status.NOT_FOUND;
import static java.lang.System.Logger.Level.ERROR;
import static java.time.ZoneOffset.UTC;

@RequiredArgsConstructor
public abstract class RequestHandler<ReqT, RespT> {

    private static final System.Logger logger = System.getLogger(RequestHandler.class.getName());

    protected static LocalDateTime toLocalDateTime(
        final Timestamp timestamp
    ) {
        return Instant
            .ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos())
            .atOffset(UTC)
            .toLocalDateTime();
    }

    protected static Timestamp toTimestamp(
        final LocalDateTime localDateTime
    ) {
        return Timestamp
            .newBuilder()
            .setSeconds(localDateTime.toEpochSecond(UTC))
            .build();
    }

    protected static <K, V> V getOrThrow(
        final Map<K, V> map,
        final K key,
        final String name
    ) {
        final V v = map.get(key);
        if (v == null) {
            throw NOT_FOUND
                .withDescription(name + " not found: " + key)
                .asRuntimeException();
        }
        return v;
    }

    protected abstract RespT getResponse(final ReqT request);

    public void handle(
        final ReqT request,
        final StreamObserver<RespT> responseObserver
    ) {
        try {
            responseObserver.onNext(getResponse(request));
            responseObserver.onCompleted();
        } catch (final Exception e) {
            logger.log(ERROR, "", e);
            responseObserver.onError(
                switch (e) {
                    case final StatusRuntimeException sre -> sre;
                    case final IllegalArgumentException iae -> Status.INVALID_ARGUMENT
                        .withDescription(iae.getMessage())
                        .withCause(iae)
                        .asRuntimeException();
                    default -> Status.INTERNAL
                        .withDescription("Unexpected server error: " + e.getMessage())
                        .withCause(e)
                        .asRuntimeException();
                }
            );
        }
    }

    @SuppressWarnings("SameParameterValue")
    protected StatusRuntimeException wrap(
        final Status status,
        final Exception e
    ) {
        return status
            .withDescription(e.getMessage())
            .withCause(e)
            .asRuntimeException();
    }

}
