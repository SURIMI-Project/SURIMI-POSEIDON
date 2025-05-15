package eu.project.surimi.poseidon.server;

import com.google.common.collect.Range;
import com.google.protobuf.Timestamp;
import eu.project.surimi.Biomass;
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

    public static Map<Species, Map<Coordinate, Double>> extractFishingActionData(
        final Simulation simulation,
        final Timestamp startDateTime,
        final Timestamp endDateTime,
        final Function<FishingAction, Bucket<?>> bucketGetter
    ) {
        final Range<LocalDateTime> dateTimeRange = Range.closed(
            toLocalDateTime(startDateTime),
            toLocalDateTime(endDateTime)
        );
        record Row(Species species, Coordinate coordinate, double biomassInKg) {}
        return simulation
            .getComponent(FishingActionAccumulator.class)
            .getEvents()
            .filter(fishingAction -> dateTimeRange.contains(fishingAction.getEndDateTime()))
            .flatMap(fishingAction ->
                bucketGetter.apply(fishingAction)
                    .getMap()
                    .entrySet()
                    .stream()
                    .map(entry -> new Row(
                        entry.getKey(),
                        fishingAction.getEndCoordinate(),
                        entry.getValue().asBiomass().asKg()
                    ))
            )
            .collect(
                groupingBy(
                    Row::species,
                    groupingBy(
                        Row::coordinate,
                        summingDouble(Row::biomassInKg)
                    )
                )
            );
    }

    public static List<Biomass.BiomassGrid> extractBiomassGrids(
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
