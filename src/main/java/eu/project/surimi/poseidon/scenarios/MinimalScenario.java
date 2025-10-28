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
import lombok.Getter;
import lombok.Setter;
import uk.ac.ox.poseidon.agents.behaviours.BehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.WaitingBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.destination.ChoosingDestinationBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.destination.ConstantDestinationSupplierFactory;
import uk.ac.ox.poseidon.agents.behaviours.disposition.CompositeDispositionProcessFactory;
import uk.ac.ox.poseidon.agents.behaviours.disposition.GeneralDiscardMortalityFactory;
import uk.ac.ox.poseidon.agents.behaviours.disposition.SelectedSpeciesRetentionFactory;
import uk.ac.ox.poseidon.agents.behaviours.fishing.DefaultFishingBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.fishing.FishingActionAccumulator;
import uk.ac.ox.poseidon.agents.behaviours.fishing.FishingActionAccumulatorFactory;
import uk.ac.ox.poseidon.agents.behaviours.port.HomeBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.port.LandingBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.strategy.ThereAndBackBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.travel.TravellingAlongPathBehaviourFactory;
import uk.ac.ox.poseidon.agents.catches.CatchCategoryFactory;
import uk.ac.ox.poseidon.agents.catches.UniformCatchCategoriserFactory;
import uk.ac.ox.poseidon.agents.fields.VesselField;
import uk.ac.ox.poseidon.agents.fields.VesselFieldFactory;
import uk.ac.ox.poseidon.agents.fisheables.CurrentCellFisheableFactory;
import uk.ac.ox.poseidon.agents.market.*;
import uk.ac.ox.poseidon.agents.vessels.*;
import uk.ac.ox.poseidon.agents.vessels.engines.SimpleEngineFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.FixedBiomassProportionGearFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.agents.vessels.holds.Hold;
import uk.ac.ox.poseidon.agents.vessels.holds.InfiniteBiomassHoldFactory;
import uk.ac.ox.poseidon.biology.biomass.*;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.biology.species.SpeciesByCodeFactory;
import uk.ac.ox.poseidon.biology.species.SpeciesFactory;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.MappedFactory;
import uk.ac.ox.poseidon.core.ScenarioSupplier;
import uk.ac.ox.poseidon.core.predicates.AlwaysTrueFactory;
import uk.ac.ox.poseidon.core.quantities.MassFactory;
import uk.ac.ox.poseidon.core.quantities.SpeedFactory;
import uk.ac.ox.poseidon.core.suppliers.ConstantDoubleSupplierFactory;
import uk.ac.ox.poseidon.core.utils.ConstantFactory;
import uk.ac.ox.poseidon.core.utils.ListFactory;
import uk.ac.ox.poseidon.geography.CoordinateFactory;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGridFromElevationValuesFactory;
import uk.ac.ox.poseidon.geography.distance.DistanceCalculator;
import uk.ac.ox.poseidon.geography.distance.HaversineDistanceCalculatorFactory;
import uk.ac.ox.poseidon.geography.grids.ModelGridFactory;
import uk.ac.ox.poseidon.geography.paths.DefaultPathFinderFactory;
import uk.ac.ox.poseidon.geography.paths.GridPathFinder;
import uk.ac.ox.poseidon.geography.ports.Port;
import uk.ac.ox.poseidon.geography.ports.PortFactory;
import uk.ac.ox.poseidon.geography.ports.PortGrid;
import uk.ac.ox.poseidon.geography.ports.PortGridFactory;
import uk.ac.ox.poseidon.io.tables.CsvTableFactory;
import uk.ac.ox.poseidon.regulations.PermittedIfFactory;

import javax.measure.Quantity;
import javax.measure.quantity.Mass;
import java.time.LocalDate;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.nCopies;
import static java.util.stream.IntStream.range;
import static si.uom.NonSI.TONNE;
import static tech.units.indriya.quantity.Quantities.getQuantity;
import static uk.ac.ox.poseidon.core.suppliers.ConstantDurationSuppliers.*;

@SuppressWarnings({"SequencedCollectionMethodCanBeUsed", "UnstableApiUsage"})
@Getter
@Setter
public class MinimalScenario extends ScenarioSupplier {

    public static final LocalDate START_DATE = LocalDate.of(2000, 1, 1);
    public static final List<String> SPECIES_CODES = List.of("A", "B", "C");
    public static final List<String> LIFE_STAGES = List.of("juvenile", "adult");

    public static final List<SimpleEntry<String, String>> LIFE_STAGE_PER_SPECIES_CODE = Streams.zip(
        // this is a just very roundabout way to generate Stream.of("A", "A", "B", "C")...
        Stream.concat(
            Stream.generate(SPECIES_CODES::getFirst).limit(LIFE_STAGES.size()),
            SPECIES_CODES.stream().skip(1)
        ),
        Stream.concat(
            LIFE_STAGES.stream(),
            Stream.generate(() -> null)
        ),
        SimpleEntry::new
    ).toList();

    public static final List<String> GEAR_CODES = List.of("G1", "G2");
    public static final List<String> MARKET_CODES = List.of("M1", "M2");
    public static final int NUM_PRICES = GEAR_CODES.size() * SPECIES_CODES.size();
    public static final Quantity<Mass> CARRYING_CAPACITY = getQuantity(1, TONNE);

    ModelGridFactory modelGrid =
        new ModelGridFactory(
            1,
            -1.5, 1.5, -1.5, 1.5
        );
    private Factory<? extends BathymetricGrid> bathymetricGrid =
        new BathymetricGridFromElevationValuesFactory(
            modelGrid,
            List.of(
                -1, -1, 0,
                -1, -1, 0,
                -1, -1, 0
            )
        );
    private Factory<? extends CarryingCapacityGrid> carryingCapacityGrid =
        new UniformCarryingCapacityGridFactory(
            bathymetricGrid,
            MassFactory.of(CARRYING_CAPACITY)
        );
    private Factory<? extends BiomassAllocator> biomassAllocator =
        new FullBiomassAllocatorFactory(carryingCapacityGrid);

    private Factory<List<Species>> species =
        new MappedFactory<>(
            new SpeciesFactory(),
            List.of("code", "lifeStage"),
            List.of(
                new ConstantFactory<>(
                    LIFE_STAGE_PER_SPECIES_CODE.stream().map(Map.Entry::getKey).toList()
                ),
                new ConstantFactory<>(
                    LIFE_STAGE_PER_SPECIES_CODE.stream().map(Map.Entry::getValue).toList()
                )
            )
        );

    private Factory<List<BiomassGrid>> biomassGrids =
        new MappedFactory<>(
            new BiomassGridFactory(
                modelGrid,
                null,
                biomassAllocator
            ),
            "species",
            species
        );
    private Factory<? extends PortGrid> portGrid =
        new PortGridFactory(bathymetricGrid);
    private Factory<? extends Port> port1 =
        new PortFactory(portGrid, "P1", "Port 1", new CoordinateFactory(1, 1));
    private Factory<? extends Port> port2 =
        new PortFactory(portGrid, "P2", "Port 2", new CoordinateFactory(1, -1));

    private Factory<BiomassMarketGrid> marketGrid =
        new BiomassMarketGridFactory(portGrid);

    private Factory<List<BiomassMarket>> markets =
        new MappedFactory<>(
            new BiomassMarketFactory(
                marketGrid,
                null,
                null,
                null
            ),
            List.of("port", "marketCode", "pricesEntries"),
            List.of(
                new ListFactory<>(port1, port2),
                new ListFactory<>(MARKET_CODES),
                new ListFactory<>(
                    Stream.of(1, 2)
                        .map(portIndex ->
                            new MappedFactory<>(
                                new PriceEntryFactory(),
                                List.of("catchCategory", "species", "price"),
                                List.of(
                                    new ListFactory<>(
                                        GEAR_CODES
                                            .stream()
                                            .map(CatchCategoryFactory::new)
                                            .flatMap(cc ->
                                                nCopies(SPECIES_CODES.size(), cc).stream()
                                            )
                                            .toList()
                                    ),
                                    new ListFactory<>(
                                        GEAR_CODES
                                            .stream()
                                            .flatMap(__ ->
                                                SPECIES_CODES.stream().map(SpeciesFactory::new)
                                            )
                                            .toList()
                                    ),
                                    new ListFactory<>(
                                        range(0, NUM_PRICES)
                                            .boxed()
                                            .map(i -> new PriceFactory(
                                                portIndex + i * 0.1,
                                                "GBP",
                                                "kg"
                                            ))
                                            .toList()
                                    )
                                )
                            )
                        )
                        .toList()
                )
            )
        );
    private Factory<? extends VesselField> vesselField =
        new VesselFieldFactory(modelGrid);
    private VesselScopeFactory<? extends Hold> hold1 =
        new InfiniteBiomassHoldFactory(
            new UniformCatchCategoriserFactory(new CatchCategoryFactory(GEAR_CODES.get(0)))
        );
    private VesselScopeFactory<? extends Hold> hold2 =
        new InfiniteBiomassHoldFactory(
            new UniformCatchCategoriserFactory(new CatchCategoryFactory(GEAR_CODES.get(1)))
        );
    private Factory<? extends DistanceCalculator> distance =
        new HaversineDistanceCalculatorFactory(modelGrid);
    private Factory<? extends GridPathFinder> pathFinder =
        new DefaultPathFinderFactory(
            bathymetricGrid,
            portGrid,
            distance
        );
    private BehaviourFactory<?> travellingBehaviour =
        new TravellingAlongPathBehaviourFactory(
            pathFinder,
            distance
        );

    private VesselScopeFactory<? extends Gear> gear1 =
        new FixedBiomassProportionGearFactory(
            GEAR_CODES.get(0),
            0.25,
            ONE_HOUR_DURATION_SUPPLIER
        );

    private VesselScopeFactory<? extends Gear> gear2 =
        new FixedBiomassProportionGearFactory(
            GEAR_CODES.get(1),
            0.5,
            ONE_HOUR_DURATION_SUPPLIER
        );

    private BehaviourFactory<?> initialBehaviour =
        new HomeBehaviourFactory(
            new AlwaysTrueFactory(),
            new ThereAndBackBehaviourFactory(
                new ChoosingDestinationBehaviourFactory(
                    new ConstantDestinationSupplierFactory(
                        modelGrid,
                        new CoordinateFactory()
                    ),
                    ONE_MINUTE_DURATION_SUPPLIER,
                    new WaitingBehaviourFactory(ONE_SECOND_DURATION_SUPPLIER)
                ),
                new DefaultFishingBehaviourFactory(
                    new CurrentCellFisheableFactory(
                        new BiomassGridsFactory(
                            biomassGrids
                        )
                    ),
                    new PermittedIfFactory(new AlwaysTrueFactory()),
                    new CompositeDispositionProcessFactory(
                        new SelectedSpeciesRetentionFactory(
                            new SpeciesByCodeFactory(
                                new ConstantFactory<>(List.of("A", "B")),
                                species
                            )
                        ),
                        new GeneralDiscardMortalityFactory(
                            new ConstantDoubleSupplierFactory(0.1)
                        )
                    )
                ),
                travellingBehaviour
            ),
            new WaitingBehaviourFactory(ONE_HOUR_DURATION_SUPPLIER),
            travellingBehaviour,
            new LandingBehaviourFactory(marketGrid, ONE_HOUR_DURATION_SUPPLIER)
        );

    private static final String COORDINATE_PROPERTY_ADDRESS =
        "initialBehaviour.behaviourIfReady.fishingDestinationBehaviour" +
            ".destinationSupplier.coordinate";

    private Factory<Fleet> fleet =
        FleetFromVesselRegisterFactory
            .builder()
            .fleet(new FleetFactory(
                vesselField,
                portGrid
            ))
            .data(CsvTableFactory.fromString("""
                cfr,name_of_vessel,place_of_registration,event,event_start_date,gear,hold,fav_lon,fav_lat
                V1,Vessel 1,P1,CEN,2000-01-01,G1,H1,-1,-1
                V2,Vessel 2,P1,CEN,2000-01-01,G2,H1,0,0
                V3,Vessel 3,P2,CEN,2000-01-01,G1,H2,0,0
                V4,Vessel 4,P2,CEN,2000-01-01,G2,H2,-1,-1
                """
            ))
            .initialBehaviour(initialBehaviour)
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
                    new UniformCatchCategoriserFactory(new CatchCategoryFactory())
                )
            )
            .dataMapping(
                "hold.catchCategoriser.catchCategory.code",
                "gear"
            )
            .engine(new SimpleEngineFactory(SpeedFactory.of("10 kn")))
            .build();

    private Factory<? extends FishingActionAccumulator> fishingActionAccumulator =
        new FishingActionAccumulatorFactory();
    private Factory<? extends BiomassSaleAccumulator> biomassSaleAccumulator =
        new BiomassSaleAccumulatorFactory();

    public MinimalScenario() {
        super(START_DATE);
    }
}
