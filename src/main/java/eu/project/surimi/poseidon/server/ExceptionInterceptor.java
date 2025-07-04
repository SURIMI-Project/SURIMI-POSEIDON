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

import io.grpc.*;

public class ExceptionInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> METHOD_NAME_KEY =
        Metadata.Key.of("method", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> APPLICATION_KEY =
        Metadata.Key.of("application", Metadata.ASCII_STRING_MARSHALLER);

    private static final String APPLICATION_NAME = "POSEIDON";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        final ServerCall<ReqT, RespT> call,
        final Metadata headers,
        final ServerCallHandler<ReqT, RespT> next
    ) {
        final String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        final ServerCall<ReqT, RespT> wrappedCall =
            new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                @Override
                public void close(
                    final Status status,
                    final Metadata trailers
                ) {
                    if (!status.isOk()) {
                        trailers.put(METHOD_NAME_KEY, fullMethodName);
                        trailers.put(APPLICATION_KEY, APPLICATION_NAME);
                    }
                    super.close(status, trailers);
                }
            };
        return next.startCall(wrappedCall, headers);
    }
}
