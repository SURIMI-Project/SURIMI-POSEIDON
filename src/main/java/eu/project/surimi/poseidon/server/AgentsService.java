package eu.project.surimi.poseidon.server;

import eu.project.surimi.Agents;
import eu.project.surimi.AgentsServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

import static lombok.AccessLevel.PACKAGE;

@RequiredArgsConstructor(access = PACKAGE)
public class AgentsService extends AgentsServiceGrpc.AgentsServiceImplBase {

    private final GetSalesSummaryRequestHandler salesSummaryRequestHandler;
    private final GetGrossCatchesRequestHandler grossCatchesRequestHandler;
    private final GetLiveDiscardsRequestHandler liveDiscardsRequestHandler;
    private final GetDeadDiscardsRequestHandler deadDiscardsRequestHandler;

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

    @Override
    public void getLiveDiscards(
        final Agents.GetLiveDiscardsRequest request,
        final StreamObserver<Agents.GetLiveDiscardsResponse> responseObserver
    ) {
        liveDiscardsRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void getDeadDiscards(
        final Agents.GetDeadDiscardsRequest request,
        final StreamObserver<Agents.GetDeadDiscardsResponse> responseObserver
    ) {
        deadDiscardsRequestHandler.handle(request, responseObserver);
    }
}
