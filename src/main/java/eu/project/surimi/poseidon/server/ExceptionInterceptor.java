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
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next
    ) {
        final String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        ServerCall<ReqT, RespT> wrappedCall =
            new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                @Override
                public void close(
                    Status status,
                    Metadata trailers
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
