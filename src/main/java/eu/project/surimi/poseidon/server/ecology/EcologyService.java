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

package eu.project.surimi.poseidon.server.ecology;

import build.buf.gen.surimi.v1.*;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import uk.ac.ox.poseidon.core.Simulation;

import java.time.LocalDate;

import static com.google.common.base.Preconditions.checkArgument;
import static eu.project.surimi.poseidon.server.Server.toLocalDateTime;
import static java.time.temporal.ChronoUnit.DAYS;

@RequiredArgsConstructor
public class EcologyService extends EcologyServiceGrpc.EcologyServiceImplBase {

    private final GetBiomassRequestHandler getBiomassRequestHandler;
    private final UpdateBiomassRequestHandler updateBiomassRequestHandler;

    @Override
    public void updateBiomass(
        final UpdateBiomassRequest request,
        final StreamObserver<UpdateBiomassResponse> responseObserver
    ) {
        updateBiomassRequestHandler.handle(request, responseObserver);
    }

    @Override
    public void getBiomass(
        final GetBiomassRequest request,
        final StreamObserver<GetBiomassResponse> responseObserver
    ) {
        getBiomassRequestHandler.handle(request, responseObserver);
    }

    static void checkRequestDateWithinOneDayOfSimulation(
        final Timestamp requestTimestamp,
        final Simulation simulation
    ) {
        final LocalDate requestDate =
            toLocalDateTime(requestTimestamp).toLocalDate();
        final LocalDate simulationDate =
            simulation.getTemporalSchedule().getDateTime().toLocalDate();
        checkArgument(
            DAYS.between(requestDate, simulationDate) <= 1,
            "Request date %s is more than one day away from current simulation date %s.".formatted(
                requestDate,
                simulationDate
            )
        );
    }
}
