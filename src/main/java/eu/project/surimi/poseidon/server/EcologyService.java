package eu.project.surimi.poseidon.server;

import eu.project.surimi.Ecology;
import eu.project.surimi.EcologyServiceGrpc;
import eu.project.surimi.Workflow;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EcologyService extends EcologyServiceGrpc.EcologyServiceImplBase {

    private final GetBiomassRequestHandler getBiomassRequestHandler;

    @Override
    public void getBiomass(
        Ecology.GetBiomassRequest request,
        StreamObserver<Ecology.GetBiomassResponse> responseObserver
    ) {
        getBiomassRequestHandler.handle(request, responseObserver);
    }
}
