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

import build.buf.gen.surimi.v1.InitRequest;
import build.buf.gen.surimi.v1.InitResponse;
import build.buf.gen.surimi.v1.UpdatePricesRequest;
import build.buf.gen.surimi.v1.UpdatePricesResponse;
import build.buf.gen.surimi.v1.WorkflowServiceGrpc.WorkflowServiceImplBase;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class WorkflowService extends WorkflowServiceImplBase {

    private final InitRequestHandler initRequestHandler;
    private final SimulateStepRequestHandler simulateStepRequestHandler;
    private final UpdatePricesRequestHandler updatePricesRequestHandler;
    private final UpdateBiomassRequestHandler updateBiomassRequestHandler;

    @Override
    public void init(
        final InitRequest request,
        final StreamObserver<InitResponse> responseObserver
    ) {
        initRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void simulateStep(
        final build.buf.gen.surimi.v1.SimulateStepRequest request,
        final StreamObserver<build.buf.gen.surimi.v1.SimulateStepResponse> responseObserver
    ) {
        simulateStepRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void updatePrices(
        final UpdatePricesRequest request,
        final StreamObserver<UpdatePricesResponse> responseObserver
    ) {
        updatePricesRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void updateBiomass(
        final build.buf.gen.surimi.v1.UpdateBiomassRequest request,
        final StreamObserver<build.buf.gen.surimi.v1.UpdateBiomassResponse> responseObserver
    ) {
        updateBiomassRequestHandler.handle(request, responseObserver);
    }

}
