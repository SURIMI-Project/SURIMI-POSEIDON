package eu.project.surimi.poseidon.server;

import eu.project.surimi.Agents;
import eu.project.surimi.AgentsServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AgentsService extends AgentsServiceGrpc.AgentsServiceImplBase {

    private final GetSalesSummaryRequestHandler salesSummaryRequestHandler;
    private final GetGrossCatchesRequestHandler grossCatchesRequestHandler;

    @Override
    public void getSalesSummary(
        final Agents.GetSalesSummaryRequest request,
        final StreamObserver<Agents.GetSalesSummaryResponse> responseObserver
    ) {
        salesSummaryRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void getGrossCatches(
        final Agents.GetGrossCatchesRequest request,
        final StreamObserver<Agents.GetGrossCatchesResponse> responseObserver
    ) {
        grossCatchesRequestHandler.handle(request, responseObserver);
    }
}
