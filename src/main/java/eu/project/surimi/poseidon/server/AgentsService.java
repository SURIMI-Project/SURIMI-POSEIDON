package eu.project.surimi.poseidon.server;

import eu.project.surimi.Agents;
import eu.project.surimi.AgentsServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AgentsService extends AgentsServiceGrpc.AgentsServiceImplBase {

    private final GetSalesSummaryRequestHandler salesSummaryRequestHandler;

    @Override
    public void getSalesSummary(
        Agents.GetSalesSummaryRequest request,
        StreamObserver<Agents.GetSalesSummaryResponse> responseObserver
    ) {
        salesSummaryRequestHandler.handle(request, responseObserver);
    }
}
