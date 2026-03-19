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

import com.google.common.collect.ImmutableMap;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import java.util.Map;

public class TrailerInterceptor implements ServerInterceptor {

    private final Map<Metadata.Key<String>, String> metadata;

    public TrailerInterceptor(final Map<String, String> metadata) {
        final ImmutableMap.Builder<Metadata.Key<String>, String> builder = ImmutableMap.builder();
        metadata.forEach((key, value) ->
            builder.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
        );
        this.metadata = builder.build();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        final ServerCall<ReqT, RespT> call,
        final Metadata headers,
        final ServerCallHandler<ReqT, RespT> next
    ) {
        return next.startCall(
            new SimpleForwardingServerCall<>(call) {
                @Override
                public void close(
                    final io.grpc.Status status,
                    final Metadata trailers
                ) {
                    metadata.forEach(trailers::put);
                    super.close(status, trailers);
                }
            },
            headers
        );
    }
}
