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
import uk.ac.ox.poseidon.agents.catches.CatchCategoryFactory;
import uk.ac.ox.poseidon.agents.catches.UniformCatchCategoriserFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.CompositeDispositionProcessFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.GeneralDiscardMortalityFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.SelectedSpeciesRetentionFactory;
import uk.ac.ox.poseidon.agents.choices.ConstantDestinationSupplierFactory;
import uk.ac.ox.poseidon.agents.fields.VesselFieldFactory;
import uk.ac.ox.poseidon.agents.fisheables.CurrentCellFisheableFactory;
import uk.ac.ox.poseidon.agents.market.*;
import uk.ac.ox.poseidon.agents.tasks.BehaviourFactory;
import uk.ac.ox.poseidon.agents.tasks.branches.SequenceTaskFactory;
import uk.ac.ox.poseidon.agents.tasks.destinations.StartTripFactory;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEventAccumulatorFactory;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingFactory;
import uk.ac.ox.poseidon.agents.tasks.landings.LandCatchesFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.RoundTripFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.TravelAlongPathFactory;
import uk.ac.ox.poseidon.agents.vessels.FleetFactory;
import uk.ac.ox.poseidon.agents.vessels.FleetFromVesselRegisterFactory;
import uk.ac.ox.poseidon.agents.vessels.VesselScopeFactoriesByCode;
import uk.ac.ox.poseidon.agents.vessels.engines.SimpleEngineFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.FixedBiomassProportionGearFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.agents.vessels.holds.InfiniteBiomassHoldFactory;
import uk.ac.ox.poseidon.biology.biomass.BiomassGridFactory;
import uk.ac.ox.poseidon.biology.biomass.CarryingCapacityGridFactory;
import uk.ac.ox.poseidon.biology.biomass.FisheableBiomassGridsFactory;
import uk.ac.ox.poseidon.biology.species.SpeciesByCodeFactory;
import uk.ac.ox.poseidon.biology.species.SpeciesFactory;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.MappedFactory;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.quantities.MassFactory;
import uk.ac.ox.poseidon.core.quantities.SpeedFactory;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.core.utils.ConstantFactory;
import uk.ac.ox.poseidon.core.utils.ListFactory;
import uk.ac.ox.poseidon.core.utils.Pair;
import uk.ac.ox.poseidon.core.utils.PairFactory;
import uk.ac.ox.poseidon.geography.CoordinateFactory;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGridFromElevationValuesFactory;
import uk.ac.ox.poseidon.geography.distance.HaversineDistanceCalculatorFactory;
import uk.ac.ox.poseidon.geography.grids.ModelGridFactory;
import uk.ac.ox.poseidon.geography.paths.DefaultPathFinderFactory;
import uk.ac.ox.poseidon.geography.ports.PortFactory;
import uk.ac.ox.poseidon.geography.ports.PortGridFactory;
import uk.ac.ox.poseidon.io.tables.CsvTableFactory;

import javax.measure.Quantity;
import javax.measure.quantity.Mass;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Collections.nCopies;
import static java.util.stream.IntStream.range;
import static si.uom.NonSI.TONNE;
import static tech.units.indriya.quantity.Quantities.getQuantity;
import static uk.ac.ox.poseidon.biology.allocators.ProportionOfCarryingCapacityAllocatorFactory.fullCarryingCapacityAllocator;
import static uk.ac.ox.poseidon.core.suppliers.ConstantDurationSuppliers.ONE_HOUR_DURATION_SUPPLIER;
import static uk.ac.ox.poseidon.core.suppliers.SupplierFactories.constantDouble;

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
            new BathymetricGridFromElevationValuesFactory<>(
                modelGrid,
                List.of(
                    -1, -1, 0,
                    -1, -1, 0,
                    -1, -1, 0
                )
            );

        final var carryingCapacityGrid =
            CarryingCapacityGridFactory.ofUniformCapacity(
                modelGrid,
                bathymetricGrid,
                MassFactory.of(CARRYING_CAPACITY)
            );

        final var biomassAllocator =
            fullCarryingCapacityAllocator(carryingCapacityGrid);

        final var species =
            new MappedFactory<>(
                new SpeciesFactory(),
                Map.of(
                    "code",
                    Factory.of(LIFE_STAGE_PER_SPECIES_CODE.stream().map(Pair::getFirst)),
                    "lifeStage",
                    Factory.of(LIFE_STAGE_PER_SPECIES_CODE.stream().map(Pair::getSecond))
                )
            );

        final var biomassGrids =
            new MappedFactory<>(
                new BiomassGridFactory(
                    modelGrid,
                    null,
                    biomassAllocator
                ),
                Map.of("species", species)
            );

        final var distance =
            new HaversineDistanceCalculatorFactory<>(modelGrid);

        final var port1 = new PortFactory("P1", "Port 1");
        final var port2 = new PortFactory("P2", "Port 2");

        final var portGrid =
            new PortGridFactory<>(
                ListFactory.from(
                    new PairFactory<>(port1, new CoordinateFactory(1, 1)),
                    new PairFactory<>(port2, new CoordinateFactory(1, -1))
                ),
                bathymetricGrid,
                distance
            );

        final ConstantFactory<List<MappedFactory<Scope, PriceEntry>>> priceEntries =
            Factory.of(
                Stream.of(1, 2).map(portIndex ->
                    new MappedFactory<>(
                        new PriceEntryFactory<>(),
                        Map.of(
                            "catchCategory", Factory.of(
                                GEAR_CODES
                                    .stream()
                                    .map(CatchCategoryFactory::new)
                                    .flatMap(cc -> nCopies(SPECIES_CODES.size(), cc).stream())
                            ),
                            "species", Factory.of(
                                GEAR_CODES
                                    .stream()
                                    .flatMap(__ -> SPECIES_CODES.stream().map(SpeciesFactory::new))
                            ),
                            "price", Factory.of(
                                range(0, NUM_PRICES)
                                    .boxed()
                                    .map(i -> new PriceFactory(
                                        portIndex + i * 0.1,
                                        "GBP",
                                        "kg"
                                    ))
                            )
                        )
                    )
                )
            );

        final var markets =
            new MappedFactory<>(
                new BiomassMarketFactory(
                    null,
                    null,
                    null
                ),
                Map.of(
                    "port", ListFactory.from(port1, port2),
                    "marketCode", Factory.of(MARKET_CODES),
                    "pricesEntries", priceEntries
                )
            );
        final var marketGrid = new MarketGridFactory<>(portGrid, markets);

        final var vesselField =
            new VesselFieldFactory(modelGrid);
        final var pathFinder =
            new DefaultPathFinderFactory<>(
                bathymetricGrid,
                portGrid,
                distance
            );

        final var gear1 =
            new FixedBiomassProportionGearFactory<>(
                GEAR_CODES.get(0),
                0.25,
                ONE_HOUR_DURATION_SUPPLIER
            );

        final var gear2 =
            new FixedBiomassProportionGearFactory<>(
                GEAR_CODES.get(1),
                0.5,
                ONE_HOUR_DURATION_SUPPLIER
            );

        final var behaviour =
            new BehaviourFactory(
                SequenceTaskFactory
                    .builder()
                    .child(
                        new RoundTripFactory(
                            new StartTripFactory(
                                new ConstantDestinationSupplierFactory(
                                    modelGrid,
                                    new CoordinateFactory()
                                )
                            ),
                            new TravelAlongPathFactory(
                                pathFinder,
                                distance
                            ),
                            new FishingFactory(
                                new CurrentCellFisheableFactory(
                                    new FisheableBiomassGridsFactory(
                                        biomassGrids
                                    )
                                ),
                                new CompositeDispositionProcessFactory<>(
                                    new SelectedSpeciesRetentionFactory<>(
                                        new SpeciesByCodeFactory<>(
                                            new ConstantFactory<>(List.of("A", "B")),
                                            species
                                        )
                                    ),
                                    new GeneralDiscardMortalityFactory<>(
                                        constantDouble(0.1)
                                    )
                                )
                            ),
                            new LandCatchesFactory(
                                ONE_HOUR_DURATION_SUPPLIER
                            )
                        )
                    )
                    .build()
            );

        final var COORDINATE_PROPERTY_ADDRESS =
            "behaviour.rootTask.children[0].startTripTask" +
                ".destinationSupplier.coordinate";

        final var fleet =
            FleetFromVesselRegisterFactory
                .builder()
                .fleet(new FleetFactory(
                    vesselField,
                    portGrid,
                    marketGrid
                ))
                .data(CsvTableFactory.fromString("""
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
                    new InfiniteBiomassHoldFactory(
                        new UniformCatchCategoriserFactory<>(new CatchCategoryFactory())
                    )
                )
                .dataMapping(
                    "hold.catchCategoriser.catchCategory.code",
                    "gear"
                )
                .engine(new SimpleEngineFactory<>(SpeedFactory.of("10 kn")))
                .build();

        final var fishingActionAccumulator =
            new FishingEventAccumulatorFactory();
        final var biomassSaleAccumulator =
            new BiomassSaleAccumulatorFactory();

        return Scenario.builder()
            .startingDateTime(START_DATE)
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
