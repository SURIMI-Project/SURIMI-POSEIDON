/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2024-2025, University of Oxford.
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

import sim.util.Int2D;
import uk.ac.ox.poseidon.agents.catches.CatchCategoryFactory;
import uk.ac.ox.poseidon.agents.catches.UniformCatchCategoriserFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.CompositeDispositionProcessFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.GeneralDiscardMortalityFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.ProportionallyLimitingBiomassToHoldFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.SelectedSpeciesRetentionFactory;
import uk.ac.ox.poseidon.agents.choices.*;
import uk.ac.ox.poseidon.agents.choices.evaluation.TotalBiomassCaughtPerHourDestinationEvaluationProviderFactory;
import uk.ac.ox.poseidon.agents.choices.evaluation.TripEvaluatorFactory;
import uk.ac.ox.poseidon.agents.fields.VesselFieldFactory;
import uk.ac.ox.poseidon.agents.fisheables.CurrentCellFisheableFactory;
import uk.ac.ox.poseidon.agents.market.BiomassMarketGridFromPriceTableFactory;
import uk.ac.ox.poseidon.agents.market.BiomassSaleAccumulatorFactory;
import uk.ac.ox.poseidon.agents.registers.DynamicRegisterFactory;
import uk.ac.ox.poseidon.agents.regulations.FishingLocationLegalityCheckerFactory;
import uk.ac.ox.poseidon.agents.tasks.Behaviour;
import uk.ac.ox.poseidon.agents.tasks.BehaviourFactory;
import uk.ac.ox.poseidon.agents.tasks.InactiveBehaviourFactory;
import uk.ac.ox.poseidon.agents.tasks.branches.SequenceTaskFactory;
import uk.ac.ox.poseidon.agents.tasks.destinations.StartTripFactory;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEventAccumulatorFactory;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingFactory;
import uk.ac.ox.poseidon.agents.tasks.general.SucceedOrWaitTaskFactory;
import uk.ac.ox.poseidon.agents.tasks.general.VesselPredicateTaskFactory;
import uk.ac.ox.poseidon.agents.tasks.general.WaitFactory;
import uk.ac.ox.poseidon.agents.tasks.landings.LandCatchesFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.EndTripFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.SetDestinationToOriginFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.TravelAlongPathFactory;
import uk.ac.ox.poseidon.agents.vessels.*;
import uk.ac.ox.poseidon.agents.vessels.engines.SimpleEngineFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.BiomassProportionPerSpeciesGearFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.agents.vessels.gears.InactiveGearFactory;
import uk.ac.ox.poseidon.agents.vessels.holds.InfiniteBiomassHoldFactory;
import uk.ac.ox.poseidon.biology.biomass.BiomassGridFactory;
import uk.ac.ox.poseidon.biology.biomass.FisheableBiomassGridsFactory;
import uk.ac.ox.poseidon.biology.biomass.FullBiomassAllocatorFactory;
import uk.ac.ox.poseidon.biology.biomass.UniformCarryingCapacityGridFactory;
import uk.ac.ox.poseidon.biology.species.SpeciesByCodeFactory;
import uk.ac.ox.poseidon.biology.species.SpeciesFromDataFactory;
import uk.ac.ox.poseidon.core.FinalProcessFactory;
import uk.ac.ox.poseidon.core.MappedFactory;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.adaptors.temporal.CurrentDayOfWeekFactory;
import uk.ac.ox.poseidon.core.adaptors.temporal.CurrentTimeFactory;
import uk.ac.ox.poseidon.core.aggregators.MaxFactory;
import uk.ac.ox.poseidon.core.events.EventClearerFactory;
import uk.ac.ox.poseidon.core.predicates.AdaptedPredicateFactory;
import uk.ac.ox.poseidon.core.predicates.InSetFactory;
import uk.ac.ox.poseidon.core.predicates.logical.AllOfFactory;
import uk.ac.ox.poseidon.core.predicates.logical.AnyOfFactory;
import uk.ac.ox.poseidon.core.predicates.numeric.AboveFactory;
import uk.ac.ox.poseidon.core.predicates.temporal.TimeIsAfterFactory;
import uk.ac.ox.poseidon.core.quantities.MassFactory;
import uk.ac.ox.poseidon.core.quantities.SpeedFactory;
import uk.ac.ox.poseidon.core.schedule.ScheduledRepeatingFactory;
import uk.ac.ox.poseidon.core.schedule.SteppableSequenceFactory;
import uk.ac.ox.poseidon.core.schedule.TemporalSchedule;
import uk.ac.ox.poseidon.core.scopes.GlobalScope;
import uk.ac.ox.poseidon.core.suppliers.ConstantDoubleSupplierFactory;
import uk.ac.ox.poseidon.core.suppliers.PoissonIntSupplierFactory;
import uk.ac.ox.poseidon.core.suppliers.ShiftedIntSupplierFactory;
import uk.ac.ox.poseidon.core.suppliers.temporal.DurationUntilSupplierFactory;
import uk.ac.ox.poseidon.core.suppliers.temporal.NextDayAtTimeSupplierFactory;
import uk.ac.ox.poseidon.core.time.DateTimeAfterStartingFactory;
import uk.ac.ox.poseidon.core.time.TimeFactory;
import uk.ac.ox.poseidon.core.utils.ConstantFactory;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGridFromGridFileFactory;
import uk.ac.ox.poseidon.geography.bathymetry.adaptors.CellElevationFactory;
import uk.ac.ox.poseidon.geography.distance.HaversineDistanceCalculatorFactory;
import uk.ac.ox.poseidon.geography.grids.CellSetFromGridFileFactory;
import uk.ac.ox.poseidon.geography.grids.ModelGridWithActiveCellsFromGridFile;
import uk.ac.ox.poseidon.geography.paths.DefaultPathFinderFactory;
import uk.ac.ox.poseidon.geography.ports.PortGridFromDataFactory;
import uk.ac.ox.poseidon.io.DirectoryRemoverFactory;
import uk.ac.ox.poseidon.io.ScenarioWriter;
import uk.ac.ox.poseidon.io.paths.PathFactory;
import uk.ac.ox.poseidon.io.paths.SimulationFolderFactory;
import uk.ac.ox.poseidon.io.tables.CsvTableFactory;
import uk.ac.ox.poseidon.regulations.ForbiddenIfFactory;
import uk.ac.ox.poseidon.regulations.predicates.spatial.ActionCellPredicateFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.function.Supplier;

import static java.time.DayOfWeek.*;
import static java.util.stream.IntStream.range;
import static uk.ac.ox.poseidon.core.suppliers.ConstantDurationSuppliers.ONE_HOUR_DURATION_SUPPLIER;
import static uk.ac.ox.poseidon.core.time.PeriodFactory.MONTHLY;

public class WesternMedScenario implements Supplier<Scenario> {

    private static final Path INPUT_PATH = Path.of("inputs", "western_med");
    private static final String CARRYING_CAPACITY = "10 kg";
    private static final double LEARNING_ALPHA = 1;
    private static final double EXPLORATION_PROBABILITY = 0.2;
    private static final int MEAN_EXPLORATION_RADIUS = 1;
    private static final double DEFAULT_CATCH_PROPORTION = 0.1;
    private static final String VESSEL_SPEED = "9.5 kn"; // as per email on 2025-03-18 08:20
    private static final String PURSE_SEINE_GEAR_CODE = "PS";
    private static final String BOTTOM_TRAWLER_GEAR_CODE = "OTB";
    private static final LocalDate START_DATE = LocalDate.of(2013, 1, 1);

    public static void main(final String[] args) {
        final int numSteps = 12 * 10;
        final Period stepSize = Period.ofMonths(1);
        final Scenario scenario = new WesternMedScenario().get();
        final Path scenarioPath = INPUT_PATH.resolve("scenario.yaml");
        new ScenarioWriter().write(scenario, scenarioPath);
        final Simulation simulation = scenario.startNewSimulation();
        final TemporalSchedule temporalSchedule = simulation.getTemporalSchedule();
        range(0, numSteps).forEach(__ ->
            temporalSchedule.stepFor(simulation, stepSize)
        );
        simulation.finish();
    }

    @Override
    public Scenario get() {
        final Scenario.ScenarioBuilder builder = Scenario.builder();

        final var inputPath = PathFactory.of(INPUT_PATH);
        final var outputPath = new SimulationFolderFactory(PathFactory.of("outputs"));

        final var modelGrid =
            new ModelGridWithActiveCellsFromGridFile<>(
                new CellSetFromGridFileFactory<>(
                    inputPath.plus("exclusion_grid.asc"),
                    0
                )
            );

        final var bathymetricGrid =
            new BathymetricGridFromGridFileFactory<>(
                inputPath.plus("bathymetry_grid.asc"),
                modelGrid,
                new MaxFactory(),
                false
            );

        final var carryingCapacityGrid =
            new UniformCarryingCapacityGridFactory(
                bathymetricGrid,
                MassFactory.of(CARRYING_CAPACITY)
            );

        final var biomassAllocator =
            new FullBiomassAllocatorFactory(carryingCapacityGrid);

        @SuppressWarnings("MagicNumber") final var regulations =
            new ForbiddenIfFactory<GlobalScope, Vessel>(
                new AnyOfFactory<>(
                    new ActionCellPredicateFactory<>(
                        modelGrid,
                        new AdaptedPredicateFactory<>(
                            new CellElevationFactory<>(bathymetricGrid),
                            new AboveFactory(-35)
                        )
                    ),
                    new ActionCellPredicateFactory<>(
                        modelGrid,
                        new InSetFactory<>(
                            new CellSetFromGridFileFactory<>(
                                inputPath.plus("french_eez.asc"),
                                1
                            )
                        )
                    )
                )
            );

        final var vesselField =
            new VesselFieldFactory(modelGrid);

        final var distance =
            new HaversineDistanceCalculatorFactory<>(modelGrid);

        final var portGrid =
            new PortGridFromDataFactory(
                CsvTableFactory.fromFile(inputPath.plus("ports.csv")),
                bathymetricGrid,
                distance,
                "port_code", "port_name", "lon", "lat"
            );

        final var pathFinder =
            new DefaultPathFinderFactory<>(
                bathymetricGrid,
                portGrid,
                distance
            );

        final var fishingLocationChecker =
            new FishingLocationLegalityCheckerFactory(
                regulations,
                pathFinder,
                distance
            );

        final var species =
            new SpeciesFromDataFactory<>(
                CsvTableFactory.fromFile(inputPath.plus("species.csv")),
                "species_code",
                "species_name",
                "life_stage"
            );

        final var fishingGear =
            VesselScopeFactoriesByCode.<Gear>builder()
                .factory(
                    PURSE_SEINE_GEAR_CODE,
                    BiomassProportionPerSpeciesGearFactory.fromFile(
                        INPUT_PATH.resolve("species.csv"),
                        "species_code",
                        "life_stage",
                        PURSE_SEINE_GEAR_CODE,
                        ONE_HOUR_DURATION_SUPPLIER,
                        species,
                        DEFAULT_CATCH_PROPORTION
                    )
                )
                .factory(
                    BOTTOM_TRAWLER_GEAR_CODE,
                    BiomassProportionPerSpeciesGearFactory.fromFile(
                        INPUT_PATH.resolve("species.csv"),
                        "species_code",
                        "life_stage",
                        BOTTOM_TRAWLER_GEAR_CODE,
                        ONE_HOUR_DURATION_SUPPLIER,
                        species,
                        DEFAULT_CATCH_PROPORTION
                    )
                )
                .defaultFactory(new InactiveGearFactory())
                .build();

        final var biomassGrids =
            new MappedFactory<>(
                new BiomassGridFactory(
                    modelGrid,
                    null,
                    biomassAllocator
                ),
                "species",
                species
            );

        final var biomassSaleAccumulator =
            new BiomassSaleAccumulatorFactory();

        final var fishingActionAccumulator =
            new FishingEventAccumulatorFactory();

        final var monthlyProcesses =
            new ScheduledRepeatingFactory<>(
                new DateTimeAfterStartingFactory(MONTHLY),
                MONTHLY,
                new SteppableSequenceFactory(
                    new EventClearerFactory(biomassSaleAccumulator),
                    new EventClearerFactory(fishingActionAccumulator)//,
//                     new CsvTableWriterFactory<>(
//                         new FishingEventListenerTableFactory(),
//                         outputPath.plus("fishing_actions.csv"),
//                         true,
//                         true
//                     )
                ),
                -2
            );

        final var marketGrid =
            new BiomassMarketGridFromPriceTableFactory(
                CsvTableFactory.fromFile(inputPath.plus("prices.csv")),
                "date",
                "market_code",
                "species_code",
                "category_code",
                "price",
                "currency",
                "measurement_unit",
                portGrid,
                species
            );

        final var optionValues =
            new ExponentialMovingAverageOptionValuesFactory<Int2D>(LEARNING_ALPHA);

        final var optionValuesRegister =
            new DynamicRegisterFactory<>(optionValues);

        final var readyForDeparture =
            new VesselPredicateTaskFactory(
                new AllOfFactory<>(
                    new AdaptedVesselPredicateFactory<>(
                        new CurrentTimeFactory(),
                        new TimeIsAfterFactory<>(new TimeFactory(21, 59, 59))
                    ),
                    new AdaptedVesselPredicateFactory<>(
                        new CurrentDayOfWeekFactory(),
                        InSetFactory.of(
                            SUNDAY,
                            MONDAY,
                            TUESDAY,
                            WEDNESDAY,
                            THURSDAY
                        )
                    )
                )
            );

        final var waitUntilNextEvening =
            new WaitFactory(
                new DurationUntilSupplierFactory(
                    new NextDayAtTimeSupplierFactory(
                        new TimeFactory(22, 0, 0)
                    )
                )
            );

        final var tripEvaluator =
            new TripEvaluatorFactory(
                optionValues,
                new TotalBiomassCaughtPerHourDestinationEvaluationProviderFactory()
            );

        final var startTrip =
            new StartTripFactory(
                new EpsilonGreedyDestinationSupplierFactory(
                    EXPLORATION_PROBABILITY,
                    new NeighbourhoodGridExplorerFactory(
                        optionValues,
                        fishingLocationChecker,
                        pathFinder,
                        new ShiftedIntSupplierFactory<>(
                            new PoissonIntSupplierFactory(MEAN_EXPLORATION_RADIUS),
                            1
                        )
                    ),
                    new ImitatingPickerFactory<>(
                        optionValues,
                        fishingLocationChecker,
                        new BestOptionsFromFriendsSupplierFactory<>(
                            5,
                            optionValuesRegister
                        )
                    )
                )
            );

        final var fishingTask = new FishingFactory(
            new CurrentCellFisheableFactory(
                new FisheableBiomassGridsFactory(biomassGrids)
            ),
            new CompositeDispositionProcessFactory<>(
                new SelectedSpeciesRetentionFactory<>(
                    new SpeciesByCodeFactory<>(
                        new ConstantFactory<>(List.of("PIL", "ANE")),
                        // TODO: we're currently restricting to a couple of species code
                        //  for testing,  but, once we have the complete list of species
                        //  for the  model (and not just the ones for which we currently
                        //  have prices) we should (probably?) restrict to species
                        //  that have a monetary value.
                        //  Preferably, we should copy what the EwE model does.
                        // new StringColumnReaderFactory(inputPath.plus("prices.csv")
                        // , "species_id"),
                        species
                    )
                ),
                new ProportionallyLimitingBiomassToHoldFactory(),
                new GeneralDiscardMortalityFactory<>(
                    new ConstantDoubleSupplierFactory(0.1)
                )
            )
        );

        final var purseSeinerBehaviour =
            new BehaviourFactory(
                SequenceTaskFactory
                    .builder()
                    .child(
                        new SucceedOrWaitTaskFactory(
                            SequenceTaskFactory
                                .builder()
                                .child(readyForDeparture)
                                .child(startTrip)
                                .build(),
                            waitUntilNextEvening
                        )
                    )
                    .child(new TravelAlongPathFactory(pathFinder, distance))
                    .child(fishingTask)
                    .child(new SetDestinationToOriginFactory())
                    .child(new TravelAlongPathFactory(pathFinder, distance))
                    .child(new LandCatchesFactory(ONE_HOUR_DURATION_SUPPLIER))
                    .child(new EndTripFactory())
                    .build()
            );

        final var bottomTrawlerBehaviour =
            new BehaviourFactory(
                SequenceTaskFactory
                    .builder()
                    .child(
                        new SucceedOrWaitTaskFactory(
                            SequenceTaskFactory
                                .builder()
                                .child(readyForDeparture)
                                .child(startTrip)
                                .build(),
                            waitUntilNextEvening
                        )
                    )
                    .child(new TravelAlongPathFactory(pathFinder, distance))
                    .child(fishingTask)
                    .child(new SetDestinationToOriginFactory())
                    .child(new TravelAlongPathFactory(pathFinder, distance))
                    .child(new LandCatchesFactory(ONE_HOUR_DURATION_SUPPLIER))
                    .child(new EndTripFactory())
                    .build()
            );

        final var behaviour =
            VesselScopeFactoriesByCode
                .<Behaviour<Vessel>>builder()
                .factory(PURSE_SEINE_GEAR_CODE, purseSeinerBehaviour)
                .factory(BOTTOM_TRAWLER_GEAR_CODE, bottomTrawlerBehaviour)
                .defaultFactory(new InactiveBehaviourFactory())
                .build();

        final var fleet =
            FleetFromVesselRegisterFactory
                .builder()
                .fleet(new FleetFactory(vesselField, portGrid, marketGrid))
                .data(CsvTableFactory.fromFile(inputPath.plus("fleet_register.csv")))
                .behaviour(behaviour)
                .dataMapping("behaviour.code", "main_fishing_gear")
                .hold(
                    new InfiniteBiomassHoldFactory(
                        new UniformCatchCategoriserFactory<>(new CatchCategoryFactory())
                    )
                )
                .dataMapping(
                    "hold.catchCategoriser.catchCategory.code",
                    "main_fishing_gear"
                )
                .gear(fishingGear)
                .dataMapping("gear.code", "main_fishing_gear")
                .dataMapping("gear.defaultFactory.code", "main_fishing_gear")
                .engine(new SimpleEngineFactory<>(SpeedFactory.of(VESSEL_SPEED)))
                .extraFactory(tripEvaluator)
                .build();

        final var directoryRemover =
            new FinalProcessFactory<>(
                new DirectoryRemoverFactory<>(outputPath, false)
            );

        builder
            .startingDateTime(START_DATE)
            .component("species", species)
            .component("bathymetricGrid", bathymetricGrid)
            .component("carryingCapacityGrid", carryingCapacityGrid)
            .component("biomassGrids", biomassGrids)
            .component("marketGrid", marketGrid)
            .component("portGrid", portGrid)
            .component("regulations", regulations)
            .component("vesselField", vesselField)
            .component("modelGrid", modelGrid)
            .component("monthlyProcesses", monthlyProcesses)
            .component("fleet", fleet)
            .component("directoryRemover", directoryRemover);

        return builder.build();
    }
}
