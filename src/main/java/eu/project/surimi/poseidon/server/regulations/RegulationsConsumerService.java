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

package eu.project.surimi.poseidon.server.regulations;

import build.buf.gen.surimi.v1.GetFishingActivityRequest;
import build.buf.gen.surimi.v1.GetFishingActivityResponse;
import build.buf.gen.surimi.v1.RegulationsConsumerServiceGrpc.RegulationsConsumerServiceImplBase;
import build.buf.gen.surimi.v1.UpdateRegulationsRequest;
import build.buf.gen.surimi.v1.UpdateRegulationsResponse;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RegulationsConsumerService extends RegulationsConsumerServiceImplBase {

    @NonNull private final UpdateRegulationsRequestHandler updateRegulationsRequestHandler;
    @NonNull private final GetFishingActivityRequestHandler getFishingActivityRequestHandler;

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
        getFishingActivityRequestHandler.handle(request, responseObserver);
    }
}
