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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import sim.engine.SimState;
import uk.ac.ox.poseidon.core.Simulation;

import java.util.Map;

import static io.grpc.Status.NOT_FOUND;
import static java.lang.System.Logger.Level.*;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;

@RequiredArgsConstructor
public abstract class RequestHandler<ReqT, RespT> {

    private static final System.Logger logger = System.getLogger(RequestHandler.class.getName());

    protected static <K, V> V getOrThrow(
        final Map<K, V> map,
        final K key,
        final String name
    ) {
        final V v = map.get(key);
        if (v == null) {
            throw NOT_FOUND
                .withDescription(
                    "%s not found: %s.%nKnown keys are:%n%s".formatted(name, key, map.keySet())
                )
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
            logger.log(DEBUG, "Received request:\n{0}", request);
            responseObserver.onNext(getResponse(request));
            responseObserver.onCompleted();
        } catch (final Exception e) {
            responseObserver.onError(
                switch (e) {
                    case final StatusRuntimeException sre -> {
                        logger.log(WARNING, "", sre);
                        yield sre;
                    }
                    case final IllegalArgumentException iae -> {
                        logger.log(WARNING, "", iae);
                        yield Status.INVALID_ARGUMENT
                            .withDescription(iae.getMessage())
                            .withCause(iae)
                            .asRuntimeException();
                    }
                    default -> {
                        logger.log(ERROR, "", e);
                        yield Status.INTERNAL
                            .withDescription("Unexpected server error: " + e.getMessage())
                            .withCause(e)
                            .asRuntimeException();
                    }
                }
            );
        }
    }

    @SuppressWarnings("SameParameterValue")
    protected static StatusRuntimeException wrap(
        final Status status,
        final Exception e
    ) {
        return status
            .withDescription(e.getMessage())
            .withCause(e)
            .asRuntimeException();
    }

    protected void log(
        final System.Logger.Level level,
        final SimState simState,
        final String format,
        final Object... args
    ) {
        Simulation.log(logger, level, simState, format, args);
    }

    protected void logMemoryUsage(
        final SimState simState
    ) {
        log(INFO, simState, "Memory usage: {0}", memoryUsage());
    }

    private static String memoryUsage() {
        final Runtime rt = Runtime.getRuntime();
        return byteCountToDisplaySize(rt.totalMemory() - rt.freeMemory()) +
            " / " + byteCountToDisplaySize(rt.maxMemory());
    }
}
