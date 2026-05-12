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

package eu.project.surimi.poseidon.server.fishery;

import build.buf.gen.surimi.v1.*;
import eu.project.surimi.poseidon.server.catchprovider.GetCatchDispositionSummaryRequestHandler;
import eu.project.surimi.poseidon.server.ecology.UpdateBiomassRequestHandler;
import eu.project.surimi.poseidon.server.prices.UpdateSpeciesPricesRequestHandler;
import eu.project.surimi.poseidon.server.regulations.GetFishingActivityRequestHandler;
import eu.project.surimi.poseidon.server.regulations.UpdateRegulationsRequestHandler;
import eu.project.surimi.poseidon.server.sales.GetSalesRequestHandler;
import eu.project.surimi.poseidon.server.simulation.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FisheryService extends build.buf.gen.surimi.v1.FisheryServiceGrpc.FisheryServiceImplBase {

    private final InitialiseRequestHandler initialiseRequestHandler;
    private final SimulateStepRequestHandler simulateStepRequestHandler;
    private final FinaliseRequestHandler finaliseRequestHandler;
    private final CancelRequestHandler cancelRequestHandler;
    private final GetCatchDispositionSummaryRequestHandler catchDispositionSummaryRequestHandler;
    private final UpdateBiomassRequestHandler updateBiomassRequestHandler;
    private final GetFishingActivityRequestHandler fishingActivityRequestHandler;
    private final UpdateRegulationsRequestHandler updateRegulationsRequestHandler;
    private final GetSalesRequestHandler salesRequestHandler;
    private final UpdateSpeciesPricesRequestHandler updateSpeciesPricesRequestHandler;
    private final GetProtocolVersionRequestHandler getProtocolVersionRequestHandler;

    @Override
    public void initialiseSimulation(
        final InitialiseSimulationRequest request,
        final StreamObserver<InitialiseSimulationResponse> responseObserver
    ) {
        initialiseRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void simulateStep(
        final build.buf.gen.surimi.v1.SimulateStepRequest request,
        final StreamObserver<build.buf.gen.surimi.v1.SimulateStepResponse> responseObserver
    ) {
        simulateStepRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void finaliseSimulation(
        final FinaliseSimulationRequest request,
        final StreamObserver<FinaliseSimulationResponse> responseObserver
    ) {
        finaliseRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void cancelSimulation(
        final CancelSimulationRequest request,
        final StreamObserver<CancelSimulationResponse> responseObserver
    ) {
        cancelRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void updateBiomass(
        final UpdateBiomassRequest request,
        final StreamObserver<UpdateBiomassResponse> responseObserver
    ) {
        updateBiomassRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void getCatchDisposition(
        final GetCatchDispositionRequest request,
        final StreamObserver<GetCatchDispositionResponse> responseObserver
    ) {
        catchDispositionSummaryRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void updateSpeciesPrices(
        final UpdateSpeciesPricesRequest request,
        final StreamObserver<UpdateSpeciesPricesResponse> responseObserver
    ) {
        updateSpeciesPricesRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void getProtocolVersion(
        final GetProtocolVersionRequest request,
        final StreamObserver<GetProtocolVersionResponse> responseObserver
    ) {
        getProtocolVersionRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void updateRegulations(
        final UpdateRegulationsRequest request,
        final StreamObserver<UpdateRegulationsResponse> responseObserver
    ) {
        updateRegulationsRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void getFishingActivity(
        final GetFishingActivityRequest request,
        final StreamObserver<GetFishingActivityResponse> responseObserver
    ) {
        fishingActivityRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void getSales(
        final GetSalesRequest request,
        final StreamObserver<GetSalesResponse> responseObserver
    ) {
        salesRequestHandler.handle(request, responseObserver);
    }
}
