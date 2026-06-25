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

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.Violation;
import build.buf.validate.Violations;
import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.*;
import io.grpc.protobuf.StatusProto;

public class ValidationInterceptor implements ServerInterceptor {

    private static final int MAX_VIOLATIONS = 20;

    private final Validator validator;

    public ValidationInterceptor(final Validator validator) {
        this.validator = validator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        final ServerCall<ReqT, RespT> call,
        final Metadata headers,
        final ServerCallHandler<ReqT, RespT> next
    ) {
        final ServerCall.Listener<ReqT> listener = next.startCall(call, headers);
        return new RequestValidationServerCallListener<>(validator, call, listener);
    }

    private static class RequestValidationServerCallListener<ReqT, RespT>
        extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final Validator validator;
        private final ServerCall<ReqT, RespT> call;

        protected RequestValidationServerCallListener(
            final Validator validator,
            final ServerCall<ReqT, RespT> call,
            final ServerCall.Listener<ReqT> delegate
        ) {
            super(delegate);
            this.validator = validator;
            this.call = call;
        }

        @Override
        public void onHalfClose() {
            if (this.call.isReady()) {
                super.onHalfClose();
            }
        }

        @Override
        public void onMessage(final ReqT message) {
            if (!(message instanceof Message)) {
                throw new IllegalArgumentException(
                    "Message is of type " +
                        message.getClass() +
                        ", not a " +
                        Message.class.getName());
            }

            try {
                final ValidationResult validationResult = validator.validate((Message) message);

                if (validationResult.isSuccess()) {
                    super.onMessage(message);
                } else {
                    final int violationCount = validationResult.getViolations().size();
                    final Violations.Builder violationsBuilder = Violations.newBuilder();
                    validationResult.getViolations().stream()
                        .limit(MAX_VIOLATIONS)
                        .map(Violation::toProto)
                        .forEach(violationsBuilder::addViolations);
                    if (violationCount > MAX_VIOLATIONS) {
                        violationsBuilder.addViolations(
                            build.buf.validate.Violation.newBuilder()
                                .setMessage(
                                    "...and " + (violationCount - MAX_VIOLATIONS)
                                        + " more violations.")
                                .build()
                        );
                    }
                    final Status status = com.google.rpc.Status.newBuilder()
                        .setCode(Code.INVALID_ARGUMENT.getNumber())
                        .setMessage(
                            Code.INVALID_ARGUMENT.getValueDescriptor().getName())
                        .addDetails(Any.pack(violationsBuilder.build()))
                        .build();
                    final StatusRuntimeException sre =
                        StatusProto.toStatusRuntimeException(status);
                    call.close(sre.getStatus(), sre.getTrailers());
                }

            } catch (final ValidationException e) {
                final Status status = com.google.rpc.Status.newBuilder()
                    .setCode(Code.INTERNAL.getNumber())
                    .setMessage(e.getMessage())
                    .build();

                throw (StatusProto.toStatusRuntimeException(status));
            }
        }
    }
}
