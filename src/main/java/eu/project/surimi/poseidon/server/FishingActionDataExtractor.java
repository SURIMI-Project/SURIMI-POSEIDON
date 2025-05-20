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

import com.google.common.collect.Range;
import com.google.protobuf.Timestamp;
import eu.project.surimi.Biomass;
import eu.project.surimi.poseidon.components.FleetIdRegister;
import uk.ac.ox.poseidon.agents.behaviours.fishing.FishingAction;
import uk.ac.ox.poseidon.agents.behaviours.fishing.FishingActionAccumulator;
import uk.ac.ox.poseidon.biology.Bucket;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.geography.Coordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static eu.project.surimi.poseidon.server.RequestHandler.toLocalDateTime;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingDouble;

public class FishingActionDataExtractor {

    private FishingActionDataExtractor() {}

    private static Map<String, Map<Species, Map<Coordinate, Double>>> extractFishingActionData(
        final Simulation simulation,
        final Timestamp startDateTime,
        final Timestamp endDateTime,
        final Function<FishingAction, Bucket<?>> bucketGetter
    ) {
        final Range<LocalDateTime> dateTimeRange = Range.closed(
            toLocalDateTime(startDateTime),
            toLocalDateTime(endDateTime)
        );
        record Row(String fleetId, Species species, Coordinate coordinate, double biomassInKg) {}
        final FleetIdRegister fleetIdRegister = simulation.getComponent(FleetIdRegister.class);
        return simulation
            .getComponent(FishingActionAccumulator.class)
            .getEvents()
            .filter(fishingAction -> dateTimeRange.contains(fishingAction.getEndDateTime()))
            .flatMap(fishingAction -> {
                final String fleetId = fleetIdRegister
                    .get(fishingAction.getVessel())
                    .orElseThrow(() -> new IllegalStateException(
                        "Fleet ID not found for vessel " + fishingAction.getVessel())
                    );
                return bucketGetter.apply(fishingAction)
                    .getMap()
                    .entrySet()
                    .stream()
                    .map(entry -> new Row(
                        fleetId,
                        entry.getKey(),
                        fishingAction.getEndCoordinate(),
                        entry.getValue().asBiomass().asKg()
                    ));
            })
            .collect(
                groupingBy(
                    Row::fleetId,
                    groupingBy(
                        Row::species,
                        groupingBy(
                            Row::coordinate,
                            summingDouble(Row::biomassInKg)
                        )
                    )
                )
            );
    }

    public static List<Biomass.FleetBiomassGrids> extractFleetBiomassGrids(
        final Simulation simulation,
        final Timestamp startDateTime,
        final Timestamp endDateTime,
        final Function<FishingAction, Bucket<?>> bucketGetter
    ) {
        return extractFishingActionData(simulation, startDateTime, endDateTime, bucketGetter)
            .entrySet()
            .stream()
            .map(entry ->
                Biomass.FleetBiomassGrids.newBuilder()
                    .setFleetId(entry.getKey())
                    .addAllBiomassGrids(
                        extractBiomassGrids(entry.getValue())
                    )
                    .build()
            )
            .toList();
    }

    private static List<Biomass.BiomassGrid> extractBiomassGrids(
        final Map<Species, Map<Coordinate, Double>> fishingActionData
    ) {
        return fishingActionData
            .entrySet()
            .stream()
            .map(speciesMapEntry ->
                Biomass.BiomassGrid.newBuilder()
                    .setSpeciesId(speciesMapEntry.getKey().getCode())
                    .addAllBiomassCells(
                        speciesMapEntry
                            .getValue()
                            .entrySet()
                            .stream()
                            .map(coordinateBiomassEntry ->
                                Biomass.BiomassCell.newBuilder()
                                    .setLongitude(coordinateBiomassEntry.getKey().getLon())
                                    .setLatitude(coordinateBiomassEntry.getKey().getLat())
                                    .setBiomass(coordinateBiomassEntry.getValue())
                                    .build()
                            )
                            .toList()
                    )
                    .build()
            )
            .toList();
    }

}
