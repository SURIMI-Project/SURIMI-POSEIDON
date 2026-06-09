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

package eu.project.surimi.poseidon.scenarios;

import com.google.common.collect.Streams;
import uk.ac.ox.poseidon.agents.catches.disposition.CompositeDispositionProcessFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.GeneralDiscardMortalityFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.SelectedSpeciesRetentionFactory;
import uk.ac.ox.poseidon.agents.choices.ConstantDestinationSupplierFactory;
import uk.ac.ox.poseidon.agents.market.*;
import static uk.ac.ox.poseidon.agents.market.Factories.biomassMarket;
import static uk.ac.ox.poseidon.agents.market.Factories.biomassSaleAccumulator;
import static uk.ac.ox.poseidon.agents.market.Factories.marketGrid;
import static uk.ac.ox.poseidon.agents.market.Factories.price;
import static uk.ac.ox.poseidon.agents.market.Factories.priceEntry;
import static uk.ac.ox.poseidon.agents.tasks.Factories.behaviour;
import uk.ac.ox.poseidon.agents.tasks.landings.LandCatchesFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.RoundTripFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.TravelAlongPathFactory;
import uk.ac.ox.poseidon.agents.vessels.FleetFactory;
import uk.ac.ox.poseidon.agents.vessels.FleetFromVesselRegisterFactory;
import uk.ac.ox.poseidon.agents.vessels.VesselScopeFactoriesByCode;
import uk.ac.ox.poseidon.agents.vessels.engines.SimpleEngineFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.biology.biomass.BiomassGridFactory;
import uk.ac.ox.poseidon.biology.species.SpeciesFactory;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.utils.Pair;
import uk.ac.ox.poseidon.core.utils.PairFactory;
import static uk.ac.ox.poseidon.geography.bathymetry.Factories.bathymetricGridFromElevationValues;
import uk.ac.ox.poseidon.geography.grids.ModelGridFactory;
import static uk.ac.ox.poseidon.geography.ports.Factories.port;
import static uk.ac.ox.poseidon.geography.ports.Factories.portGrid;

import javax.measure.Quantity;
import javax.measure.quantity.Mass;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Collections.nCopies;
import static java.util.stream.IntStream.range;
import static si.uom.NonSI.KNOT;
import static si.uom.NonSI.TONNE;
import static tech.units.indriya.quantity.Quantities.getQuantity;
import static tech.units.indriya.unit.Units.LITRE;
import static uk.ac.ox.poseidon.agents.catches.Factories.catchCategory;
import static uk.ac.ox.poseidon.agents.catches.Factories.uniformCatchCategoriser;
import static uk.ac.ox.poseidon.agents.fields.Factories.vesselField;
import static uk.ac.ox.poseidon.agents.fisheables.Factories.currentCellFisheable;
import static uk.ac.ox.poseidon.agents.tasks.destinations.Factories.startTrip;
import static uk.ac.ox.poseidon.agents.tasks.fishing.Factories.fishing;
import static uk.ac.ox.poseidon.agents.tasks.fishing.Factories.fishingEventAccumulator;
import static uk.ac.ox.poseidon.agents.vessels.engines.Factories.fullTank;
import static uk.ac.ox.poseidon.agents.vessels.gears.Factories.fixedBiomassProportionGear;
import static uk.ac.ox.poseidon.agents.vessels.holds.Factories.infiniteBiomassHold;
import static uk.ac.ox.poseidon.biology.allocators.Factories.fullCarryingCapacityAllocator;
import static uk.ac.ox.poseidon.biology.biomass.Factories.*;
import static uk.ac.ox.poseidon.biology.species.Factories.species;
import static uk.ac.ox.poseidon.biology.species.Factories.speciesByCode;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.constant;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.constantDouble;
import static uk.ac.ox.poseidon.core.quantities.Factories.*;
import static uk.ac.ox.poseidon.core.time.Factories.hours;
import static uk.ac.ox.poseidon.core.time.Factories.startOf;
import static uk.ac.ox.poseidon.core.utils.Factories.*;
import static uk.ac.ox.poseidon.geography.Factories.coordinate;
import static uk.ac.ox.poseidon.geography.distance.Factories.haversineDistanceCalculator;
import static uk.ac.ox.poseidon.geography.paths.Factories.pathFinder;
import static uk.ac.ox.poseidon.io.tables.Factories.csvTableFromString;

@SuppressWarnings("UnstableApiUsage")
public class MinimalScenario implements Supplier<Scenario> {

    public static final LocalDate START_DATE = LocalDate.of(2000, 1, 1);
    public static final List<String> SPECIES_CODES = List.of("A", "B", "C");
    public static final List<String> LIFE_STAGES = List.of("juvenile", "adult");

    public static final List<Pair<String, String>> LIFE_STAGE_PER_SPECIES_CODE = Streams.zip(
        // this is a just very roundabout way to generate Stream.of("A", "A", "B", "C")...
        Stream.concat(
            Stream.generate(SPECIES_CODES::getFirst).limit(LIFE_STAGES.size()),
            SPECIES_CODES.stream().skip(1)
        ),
        Stream.concat(
            LIFE_STAGES.stream(),
            Stream.generate(() -> null)
        ),
        Pair::of
    ).toList();

    public static final List<String> GEAR_CODES = List.of("G1", "G2");
    public static final List<String> MARKET_CODES = List.of("M1", "M2");
    public static final int NUM_PRICES = GEAR_CODES.size() * SPECIES_CODES.size();
    public static final Quantity<Mass> CARRYING_CAPACITY = getQuantity(1, TONNE);

    @Override
    public Scenario get() {

        final ModelGridFactory modelGrid =
            new ModelGridFactory(
                1,
                -1.5, 1.5, -1.5, 1.5
            );
        final var bathymetricGrid =
            bathymetricGridFromElevationValues(
                modelGrid,
                List.of(
                    -1, -1, 0,
                    -1, -1, 0,
                    -1, -1, 0
                )
            );

        final var carryingCapacityGrid =
            uniformCarryingCapacityGrid(
                modelGrid,
                bathymetricGrid,
                massOf(CARRYING_CAPACITY)
            );

        final var biomassAllocator =
            fullCarryingCapacityAllocator(carryingCapacityGrid);

        final var species =
            mappedFactory(
                species(),
                mappedProperty(
                    SpeciesFactory::setCode,
                    LIFE_STAGE_PER_SPECIES_CODE.stream().map(Pair::getFirst).toList()
                ),
                mappedProperty(
                    SpeciesFactory::setLifeStage,
                    LIFE_STAGE_PER_SPECIES_CODE.stream().map(Pair::getSecond).toList()
                )
            );

        final var biomassGrids =
            mappedFactory(
                biomassGrid(modelGrid, null, biomassAllocator),
                mappedProperty(BiomassGridFactory::setSpecies, species.getFactories())
            );

        final var distance =
            haversineDistanceCalculator(modelGrid);

        final var port1 = port("P1", "Port 1");
        final var port2 = port("P2", "Port 2");

        final var portGrid =
            portGrid(
                listOf(
                    new PairFactory<>(port1, coordinate(1, 1)),
                    new PairFactory<>(port2, coordinate(1, -1))
                ),
                bathymetricGrid,
                distance
            );

        final var priceEntries =
            Stream.of(1, 2).map(portIndex ->
                mappedFactory(
                    priceEntry(null, null, null),
                    mappedProperty(
                        PriceEntryFactory::setCatchCategory,
                        GEAR_CODES
                            .stream()
                            .map(code -> catchCategory(code))
                            .flatMap(cc -> nCopies(SPECIES_CODES.size(), cc).stream())
                            .toList()
                    ),
                    mappedProperty(
                        PriceEntryFactory::setSpecies,
                        GEAR_CODES
                            .stream()
                            .flatMap(_ -> SPECIES_CODES.stream().map(code -> species(code)))
                            .toList()
                    ),
                    mappedProperty(
                        PriceEntryFactory::setPrice,
                        range(0, NUM_PRICES)
                            .boxed()
                            .map(i -> price(
                                portIndex + i * 0.1,
                                "GBP",
                                "kg"
                            ))
                            .toList()
                    )
                )
            ).toList();

        final var markets =
            mappedFactory(
                biomassMarket(null, null, null),
                mappedProperty(BiomassMarketFactory::setPort, List.of(port1, port2)),
                mappedProperty(BiomassMarketFactory::setMarketCode, MARKET_CODES),
                mappedProperty(BiomassMarketFactory::setPricesEntries, priceEntries)
            );

        final var marketGrid = marketGrid(portGrid, markets);

        final var vesselField =
            vesselField(modelGrid);
        final var pathFinder =
            pathFinder(
                bathymetricGrid,
                portGrid,
                distance
            );

        final var gear1 =
            fixedBiomassProportionGear(
                GEAR_CODES.get(0),
                0.25,
                constant(hours(1))
            );

        final var gear2 =
            fixedBiomassProportionGear(
                GEAR_CODES.get(1),
                0.5,
                constant(hours(1))
            );

        final var behaviour =
            behaviour(
                new RoundTripFactory(
                    startTrip(
                        new ConstantDestinationSupplierFactory(
                            modelGrid,
                            coordinate(0, 0)
                        )
                    ),
                    new TravelAlongPathFactory(
                        pathFinder,
                        distance
                    ),
                    fishing(
                        currentCellFisheable(
                            fisheableBiomassGrids(
                                biomassGrids
                            )
                        ),
                        new CompositeDispositionProcessFactory<>(
                            new SelectedSpeciesRetentionFactory<>(
                                speciesByCode(
                                    listOf("A", "B"),
                                    species
                                )
                            ),
                            new GeneralDiscardMortalityFactory<>(
                                constantDouble(0.1)
                            )
                        )
                    ),
                    new LandCatchesFactory(constant(hours(1)))
                )
            );

        final var COORDINATE_PROPERTY_ADDRESS =
            "behaviour.rootTask.startTripTask" +
                ".destinationSupplier.coordinate";

        final var fleet =
            FleetFromVesselRegisterFactory
                .builder()
                .fleet(new FleetFactory(
                    vesselField,
                    portGrid,
                    marketGrid
                ))
                .data(csvTableFromString("""
                    cfr,name_of_vessel,place_of_registration,event,event_start_date,gear,hold,fav_lon,fav_lat
                    V1,Vessel 1,P1,CEN,2000-01-01,G1,H1,-1,-1
                    V2,Vessel 2,P1,CEN,2000-01-01,G2,H1,0,0
                    V3,Vessel 3,P2,CEN,2000-01-01,G1,H2,0,0
                    V4,Vessel 4,P2,CEN,2000-01-01,G2,H2,-1,-1
                    """
                ))
                .behaviour(behaviour)
                .dataMapping(
                    COORDINATE_PROPERTY_ADDRESS + ".longitude",
                    "fav_lon"
                )
                .dataMapping(
                    COORDINATE_PROPERTY_ADDRESS + ".latitude",
                    "fav_lat"
                )
                .gear(
                    VesselScopeFactoriesByCode.<Gear>builder()
                        .factory("G1", gear1)
                        .factory("G2", gear2)
                        .build()
                )
                .dataMapping("gear.code", "gear")
                .hold(
                    infiniteBiomassHold(
                        uniformCatchCategoriser(catchCategory(null))
                    )
                )
                .dataMapping(
                    "hold.catchCategoriser.catchCategory.code",
                    "gear"
                )
                .engine(
                    new SimpleEngineFactory<>(
                        fullTank(volumeOf(100000, LITRE)),
                        speedOf(10, KNOT),
                        volumeOf(0, LITRE) // don't consume any fuel
                    )
                )
                .build();

        final var fishingActionAccumulator =
            fishingEventAccumulator();
        final var biomassSaleAccumulator =
            biomassSaleAccumulator();

        return Scenario.builder()
            .startingDateTime(startOf(START_DATE))
            .component("modelGrid", modelGrid)
            .component("species", species)
            .component("biomassGrids", biomassGrids)
            .component("bathymetricGrid", bathymetricGrid)
            .component("carryingCapacityGrid", carryingCapacityGrid)
            .component("fleet", fleet)
            .component("vesselField", vesselField)
            .component("portGrid", portGrid)
            .component("markets", markets)
            .component("marketGrid", marketGrid)
            .component("fishingActionAccumulator", fishingActionAccumulator)
            .component("biomassSaleAccumulator", biomassSaleAccumulator)
            .build();
    }
}
