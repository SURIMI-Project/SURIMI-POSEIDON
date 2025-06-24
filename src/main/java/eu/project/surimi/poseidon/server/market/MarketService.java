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

package eu.project.surimi.poseidon.server.market;

import build.buf.gen.surimi.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MarketService extends MarketServiceGrpc.MarketServiceImplBase {

    private final GetSalesRequestHandler salesRequestHandler;
    private final UpdateSpeciesPricesRequestHandler updateSpeciesPricesRequestHandler;

    @Override
    public void getSpeciesPrices(
        final GetSpeciesPricesRequest request,
        final StreamObserver<GetSpeciesPricesResponse> responseObserver
    ) {
        // TODO
        super.getSpeciesPrices(request, responseObserver);
    }

    @Override
    public void updateSpeciesPrices(
        final UpdateSpeciesPricesRequest request,
        final StreamObserver<UpdateSpeciesPricesResponse> responseObserver
    ) {
        updateSpeciesPricesRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void getSales(
        final GetSalesRequest request,
        final StreamObserver<GetSalesResponse> responseObserver
    ) {
        salesRequestHandler.handle(request, responseObserver);
    }

}
