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

package eu.project.surimi.poseidon.server.fishery;

import build.buf.gen.surimi.v1.CatchDispositionSummary;
import build.buf.gen.surimi.v1.DispositionGrid;
import build.buf.gen.surimi.v1.GetCatchDispositionRequest;
import build.buf.gen.surimi.v1.GetCatchDispositionResponse;
import com.google.common.collect.Range;
import com.google.protobuf.Timestamp;
import eu.project.surimi.poseidon.server.SimulationManager;
import eu.project.surimi.poseidon.server.WithSimulationRequestHandler;
import uk.ac.ox.poseidon.agents.behaviours.fishing.FishingActionAccumulator;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.geography.Coordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static eu.project.surimi.poseidon.server.Server.toLocalDateTime;
import static java.util.stream.Collectors.*;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class GetCatchDispositionRequestHandler
    extends WithSimulationRequestHandler<GetCatchDispositionRequest, GetCatchDispositionResponse> {

    public GetCatchDispositionRequestHandler(final SimulationManager simulationManager) {
        super(simulationManager);
    }

    private static Map<String, Map<Species, Map<Coordinate, Disposition>>> extractFishingActionData(
        final Simulation simulation,
        final Timestamp startDateTime,
        final Timestamp endDateTime
    ) {
        final Range<LocalDateTime> dateTimeRange = Range.closed(
            toLocalDateTime(startDateTime),
            toLocalDateTime(endDateTime)
        );
        record Row(
            String gearCode,
            Species species,
            Coordinate coordinate,
            Disposition disposition
        ) {}
        return simulation
            .getComponent(FishingActionAccumulator.class)
            .getEvents()
            .filter(fishingAction -> dateTimeRange.contains(fishingAction.getEndDateTime()))
            .flatMap(fishingAction ->
                fishingAction.getGrossCatch()
                    .getMap()
                    .entrySet()
                    .stream()
                    .map(entry -> new Row(
                        fishingAction.getFishingGear().getCode(),
                        entry.getKey(),
                        fishingAction.getEndCoordinate(),
                        new Disposition(
                            entry.getValue().asBiomass().asKg(),
                            fishingAction
                                .getDisposition()
                                .getDiscardedAlive()
                                .getContent(entry.getKey())
                                .map(c -> c.asBiomass().asKg())
                                .orElse(0.0),
                            fishingAction
                                .getDisposition()
                                .getDiscardedDead()
                                .getContent(entry.getKey())
                                .map(c -> c.asBiomass().asKg())
                                .orElse(0.0)
                        )
                    )))
            .collect(
                groupingBy(
                    Row::gearCode,
                    groupingBy(
                        Row::species,
                        groupingBy(
                            Row::coordinate,
                            collectingAndThen(
                                toList(), rows -> {
                                    final List<Disposition> dispositions = rows
                                        .stream()
                                        .map(Row::disposition)
                                        .toList();
                                    return new Disposition(
                                        dispositions.stream()
                                            .mapToDouble(Disposition::grossCatchInKg)
                                            .sum(),
                                        dispositions.stream()
                                            .mapToDouble(Disposition::liveDiscardsInKg)
                                            .sum(),
                                        dispositions.stream()
                                            .mapToDouble(Disposition::deadDiscardsInKg)
                                            .sum()
                                    );
                                }
                            )
                        )
                    )
                )
            );
    }

    @Override
    protected String getSimulationId(final GetCatchDispositionRequest request) {
        return request.getSimulationId();
    }

    @Override
    protected GetCatchDispositionResponse getResponseWithSimulation(
        final GetCatchDispositionRequest request,
        final Simulation simulation
    ) {
        final CatchDispositionSummary.Builder catchDispositionSummaryBuilder =
            CatchDispositionSummary
                .newBuilder()
                .setMeasurementUnit(KILOGRAM.getSymbol());

        final GetCatchDispositionResponse.Builder responseBuilder =
            GetCatchDispositionResponse
                .newBuilder()
                .setSimulationId(request.getSimulationId())
                .setCatchDispositionSummary(catchDispositionSummaryBuilder);

        extractFishingActionData(
            simulation,
            request.getStartDateTime(),
            request.getEndDateTime()
        ).forEach((gearCode, speciesData) -> {
            speciesData.forEach((species, coordinateData) -> {
                final DispositionGrid.Builder
                    dispositionGridsBuilder =
                    catchDispositionSummaryBuilder
                        .addDispositionGridsBuilder()
                        .setGearCode(gearCode)
                        .setSpeciesCode(species.getCode());
                coordinateData.forEach((coordinate, disposition) ->
                    dispositionGridsBuilder
                        .addDispositionCellsBuilder()
                        .setLongitude(coordinate.lon)
                        .setLatitude(coordinate.lat)
                        .setGrossCatch(disposition.grossCatchInKg)
                        .setLiveDiscards(disposition.liveDiscardsInKg)
                        .setDeadDiscards(disposition.deadDiscardsInKg));
            });
        });
        return responseBuilder.build();
    }

    record Disposition(
        double grossCatchInKg,
        double liveDiscardsInKg,
        double deadDiscardsInKg
    ) {}

}
