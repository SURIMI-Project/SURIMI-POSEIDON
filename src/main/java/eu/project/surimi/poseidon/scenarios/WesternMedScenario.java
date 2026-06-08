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
import uk.ac.ox.poseidon.agents.catches.disposition.ProportionallyLimitingBiomassToHoldFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.SpeciesSpecificDiscardMortalityRatesFactory;
import uk.ac.ox.poseidon.agents.catches.disposition.SpeciesSpecificDiscardRatesFactory;
import uk.ac.ox.poseidon.agents.choices.*;
import uk.ac.ox.poseidon.agents.components.ComponentRegisterFactory;
import static uk.ac.ox.poseidon.agents.fields.Factories.vesselField;
import static uk.ac.ox.poseidon.agents.fisheables.Factories.currentCellFisheable;
import static uk.ac.ox.poseidon.agents.fuel.Factories.fuelStationGrid;
import static uk.ac.ox.poseidon.agents.fuel.Factories.oneFuelStationPerPort;
import uk.ac.ox.poseidon.agents.market.BiomassMarketGridFromPriceTableFactory;
import uk.ac.ox.poseidon.agents.market.BiomassSaleAccumulatorFactory;
import static uk.ac.ox.poseidon.agents.regulations.predicates.Factories.fishingLocationLegalityChecker;
import uk.ac.ox.poseidon.agents.tasks.Behaviour;
import uk.ac.ox.poseidon.agents.tasks.BehaviourFactory;
import uk.ac.ox.poseidon.agents.tasks.InactiveBehaviourFactory;
import static uk.ac.ox.poseidon.agents.tasks.destinations.Factories.startTrip;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEventAccumulatorFactory;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingFactory;
import uk.ac.ox.poseidon.agents.tasks.general.SucceedOrWaitTaskFactory;
import uk.ac.ox.poseidon.agents.tasks.landings.LandCatchesFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.EndTripFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.SetDestinationToOriginFactory;
import uk.ac.ox.poseidon.agents.tasks.travel.TravelAlongPathFactory;
import uk.ac.ox.poseidon.agents.vessels.FleetFactory;
import uk.ac.ox.poseidon.agents.vessels.FleetFromVesselRegisterFactory;
import uk.ac.ox.poseidon.agents.vessels.VesselScopeFactoriesByCode;
import uk.ac.ox.poseidon.agents.vessels.engines.SimpleEngineFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.agents.vessels.gears.InactiveGearFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.SpeciesSpecificBiomassCatchabilityGearFactory;
import uk.ac.ox.poseidon.agents.vessels.holds.InfiniteBiomassHoldFactory;

import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import static uk.ac.ox.poseidon.core.events.Factories.eventClearer;
import uk.ac.ox.poseidon.core.schedule.SteppableSequenceFactory;
import uk.ac.ox.poseidon.core.schedule.TemporalSchedule;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.core.utils.FinalProcessFactory;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGridFromGridFileFactory;
import uk.ac.ox.poseidon.geography.distance.HaversineDistanceCalculatorFactory;
import uk.ac.ox.poseidon.geography.grids.ModelGridFromGridFile;
import uk.ac.ox.poseidon.geography.grids.ModelGridWithActiveCellsFactory;
import uk.ac.ox.poseidon.geography.ports.PortGrid;
import uk.ac.ox.poseidon.geography.ports.PortGridFactory;
import uk.ac.ox.poseidon.geography.ports.PortsFromTableFactory;
import static uk.ac.ox.poseidon.io.Factories.directoryRemover;
import uk.ac.ox.poseidon.io.ScenarioWriter;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.function.Supplier;

import static eu.project.surimi.poseidon.regulations.Factories.totalAllowableCatchQuotas;
import static eu.project.surimi.poseidon.server.fleet.Factories.fleetSegmentMapper;
import static java.time.DayOfWeek.*;
import static java.util.stream.IntStream.range;
import static si.uom.NonSI.KNOT;
import static tech.units.indriya.unit.Units.LITRE;
import static uk.ac.ox.poseidon.agents.choices.evaluation.Factories.totalBiomassCaughtPerHour;
import static uk.ac.ox.poseidon.agents.choices.evaluation.Factories.tripEvaluator;
import static uk.ac.ox.poseidon.agents.components.Factories.component;
import static uk.ac.ox.poseidon.agents.money.Factories.money;
import static uk.ac.ox.poseidon.agents.money.Factories.moneyFromRow;
import static uk.ac.ox.poseidon.agents.regulations.actions.Factories.departNow;
import static uk.ac.ox.poseidon.agents.tasks.accounting.Factories.payTripCost;
import static uk.ac.ox.poseidon.agents.tasks.branches.Factories.sequenceTask;
import static uk.ac.ox.poseidon.agents.tasks.general.Factories.checkThat;
import static uk.ac.ox.poseidon.agents.tasks.general.Factories.waitFor;
import static uk.ac.ox.poseidon.agents.tasks.landings.Factories.landCatches;
import static uk.ac.ox.poseidon.agents.tasks.travel.Factories.refuel;
import static uk.ac.ox.poseidon.agents.vessels.accounts.Factories.fixedCostCollector;
import static uk.ac.ox.poseidon.agents.vessels.engines.Factories.fullTank;
import static uk.ac.ox.poseidon.agents.vessels.extractors.tags.Factories.doubleTagExtractor;
import static uk.ac.ox.poseidon.agents.vessels.extractors.tags.Factories.stringTagExtractor;
import static uk.ac.ox.poseidon.biology.allocators.Factories.fullCarryingCapacityAllocator;
import static uk.ac.ox.poseidon.biology.biomass.Factories.biomassGrids;
import static uk.ac.ox.poseidon.biology.biomass.Factories.fisheableBiomassGrids;
import static uk.ac.ox.poseidon.biology.biomass.Factories.uniformCarryingCapacityGrid;
import static uk.ac.ox.poseidon.biology.species.Factories.speciesFromData;
import static uk.ac.ox.poseidon.core.aggregators.Factories.maxAggregator;
import static uk.ac.ox.poseidon.core.functions.Factories.*;
import static uk.ac.ox.poseidon.core.functions.NumericIntervalToStringMapperFactory.interval;
import static uk.ac.ox.poseidon.core.predicates.Factories.condition;
import static uk.ac.ox.poseidon.core.predicates.Factories.in;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.allOf;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.anyOf;
import static uk.ac.ox.poseidon.core.predicates.numeric.Factories.greaterThan;
import static uk.ac.ox.poseidon.core.predicates.temporal.Factories.afterTime;
import static uk.ac.ox.poseidon.core.providers.Factories.shiftedInt;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.constant;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.constantInt;
import static uk.ac.ox.poseidon.core.providers.math.Factories.maxInt;
import static uk.ac.ox.poseidon.core.providers.math.Factories.minInt;
import static uk.ac.ox.poseidon.core.providers.random.Factories.randomPoisson;
import static uk.ac.ox.poseidon.core.providers.temporal.Factories.*;
import static uk.ac.ox.poseidon.core.quantities.Factories.*;
import static uk.ac.ox.poseidon.core.schedule.Factories.scheduledRepeating;
import static uk.ac.ox.poseidon.core.schedule.Factories.scheduledRepeatingFromStart;
import static uk.ac.ox.poseidon.core.time.Factories.*;
import static uk.ac.ox.poseidon.core.utils.Factories.listOf;
import static uk.ac.ox.poseidon.core.utils.Factories.setOf;
import static uk.ac.ox.poseidon.geography.grids.Factories.cellSetFromGridFile;
import static uk.ac.ox.poseidon.geography.grids.extractors.Factories.cellValue;
import static uk.ac.ox.poseidon.geography.paths.Factories.pathFinder;
import static uk.ac.ox.poseidon.io.paths.Factories.path;
import static uk.ac.ox.poseidon.io.paths.Factories.simulationFolder;
import static uk.ac.ox.poseidon.io.tables.Factories.*;
import static uk.ac.ox.poseidon.regulations.Factories.forbiddenIf;
import static uk.ac.ox.poseidon.regulations.predicates.Factories.isPermitted;
import static uk.ac.ox.poseidon.regulations.predicates.spatial.Factories.actionCellPredicate;

public class WesternMedScenario implements Supplier<Scenario> {

    public static final LocalDate START_DATE = LocalDate.of(2013, 1, 1);
    private static final Path INPUT_PATH = Path.of("inputs", "western_med");
    private static final String CARRYING_CAPACITY = "10 kg";
    private static final double LEARNING_ALPHA = 1;
    private static final double EXPLORATION_PROBABILITY = 0.2;
    private static final int MEAN_EXPLORATION_RADIUS = 1;
    private static final double DEFAULT_CATCH_PROPORTION = 0.1;
    private static final double DEFAULT_PURSE_SEINE_DISCARD_RATE = 0.05;
    private static final double DEFAULT_BOTTOM_TRAWLER_DISCARD_RATE = 0.2;
    private static final double DEFAULT_PURSE_SEINE_DISCARD_MORTALITY_RATE = 0.1;
    private static final double DEFAULT_BOTTOM_TRAWLER_DISCARD_MORTALITY_RATE = 0.3;
    private static final double PURSE_SEINER_DEPTH_THRESHOLD = -35.0;
    private static final double VESSEL_SPEED_IN_KNOTS = 9.5; // as per email on 2025-03-18 08:20
    private static final String PURSE_SEINE_GEAR_CODE = "PS";
    private static final String BOTTOM_TRAWLER_GEAR_CODE = "OTB";

    static void main(final String[] args) {
        final int numSteps = 12 * 10;
        final Period stepSize = Period.ofMonths(1);
        final Scenario scenario = new WesternMedScenario().get();
        final Path scenarioPath = INPUT_PATH.resolve("scenario.yaml");
        new ScenarioWriter().write(scenario, scenarioPath);
        final Simulation simulation = scenario.startNewSimulation();
        final TemporalSchedule temporalSchedule = simulation.getTemporalSchedule();
        range(0, numSteps).forEach(_ -> {
            temporalSchedule.stepFor(simulation, stepSize);
            System.out.println(temporalSchedule.getDateTime());
        });
        simulation.finish();
    }

    @Override
    public Scenario get() {
        final Scenario.ScenarioBuilder builder = Scenario.builder();

        final var inputPath = path(INPUT_PATH);
        final var outputPath = simulationFolder(path("outputs"));
        final var bathymetricGridPath = inputPath.plus("bathymetry_grid.asc");

        final var modelGrid =
            new ModelGridWithActiveCellsFactory<>(
                new ModelGridFromGridFile<>(bathymetricGridPath),
                cellSetFromGridFile(
                    inputPath.plus("exclusion_grid.asc"),
                    0
                )
            );

        final var bathymetricGrid =
            new BathymetricGridFromGridFileFactory<>(
                bathymetricGridPath,
                modelGrid,
                maxAggregator(),
                false
            );

        final var carryingCapacityGrid =
            uniformCarryingCapacityGrid(
                modelGrid,
                bathymetricGrid,
                massOf(CARRYING_CAPACITY)
            );

        final var biomassAllocator =
            fullCarryingCapacityAllocator(carryingCapacityGrid);

        final var regulations =
            forbiddenIf(
                anyOf(
                    actionCellPredicate(
                        modelGrid,
                        condition(
                            cellValue(bathymetricGrid),
                            greaterThan(PURSE_SEINER_DEPTH_THRESHOLD)
                        )
                    ),
                    actionCellPredicate(
                        modelGrid,
                        in(
                            cellSetFromGridFile(
                                inputPath.plus("french_eez.asc"),
                                1
                            )
                        )
                    )
                )
            );

        final var vesselField =
            vesselField(modelGrid);

        final var distance =
            new HaversineDistanceCalculatorFactory<>(modelGrid);

        final Factory<Scope, ? extends PortGrid> portGrid =
            new PortGridFactory<>(
                new PortsFromTableFactory<>(
                    csvTableFromFile(inputPath.plus("ports.csv")),
                    "port_code", "port_name", "lon", "lat"
                ),
                bathymetricGrid,
                distance
            );

        final var pathFinder =
            pathFinder(
                bathymetricGrid,
                portGrid,
                distance
            );

        final var fishingLocationChecker =
            fishingLocationLegalityChecker(
                regulations,
                pathFinder,
                distance
            );

        final var species =
            speciesFromData(
                csvTableFromFile(inputPath.plus("species.csv")),
                "species_code",
                "species_name",
                "life_stage"
            );

        final var fishingGear =
            VesselScopeFactoriesByCode.<Gear>builder()
                .factory(
                    PURSE_SEINE_GEAR_CODE,
                    SpeciesSpecificBiomassCatchabilityGearFactory.fromFile(
                        INPUT_PATH.resolve("species.csv"),
                        "species_code",
                        "life_stage",
                        PURSE_SEINE_GEAR_CODE,
                        constant(hours(1)),
                        species,
                        DEFAULT_CATCH_PROPORTION
                    )
                )
                .factory(
                    BOTTOM_TRAWLER_GEAR_CODE,
                    SpeciesSpecificBiomassCatchabilityGearFactory.fromFile(
                        INPUT_PATH.resolve("species.csv"),
                        "species_code",
                        "life_stage",
                        BOTTOM_TRAWLER_GEAR_CODE,
                        constant(hours(1)),
                        species,
                        DEFAULT_CATCH_PROPORTION
                    )
                )
                .defaultFactory(new InactiveGearFactory())
                .build();

        final var biomassGrids =
            biomassGrids(
                modelGrid,
                species,
                listOf(biomassAllocator)
            );

        final var biomassSaleAccumulator =
            new BiomassSaleAccumulatorFactory();

        final var fishingActionAccumulator =
            new FishingEventAccumulatorFactory();

        final var monthlyProcesses =
            scheduledRepeating(
                dateTimeAfterStarting(ONE_MONTH),
                MONTHLY,
                new SteppableSequenceFactory(
                    listOf(
                        eventClearer(biomassSaleAccumulator),
                        eventClearer(fishingActionAccumulator)
                    )
                ),
                -2
            );

        final var costsKeyFromRow =
            multiKeyFromRow("country_code", "year", "vessel_length", "gear");

        final var hourlyCostsMap =
            mapFromTable(
                csvTableFromFile(inputPath.plus("operating_costs.csv")),
                costsKeyFromRow,
                moneyFromRow("currency", "cost_per_hour_at_sea")
            );

        final var vesselLengthClassMapper =
            numericIntervalToStringMapper(
                interval(0.0, 6.0, "VL0006"),
                interval(6.0, 12.0, "VL0612"),
                interval(12.0, 18.0, "VL1218"),
                interval(18.0, 24.0, "VL1824"),
                interval(24.0, 40.0, "VL2440"),
                interval(40.0, null, "VL40XX")
            );

        final var costsKeyFromVessel =
            multiKeyFromFunctions(
                stringTagExtractor("country_of_registration"),
                minInt(maxInt(currentYear(), constantInt(2013)), constantInt(2023)),
                composedFunction(
                    doubleTagExtractor("loa"),
                    vesselLengthClassMapper
                ),
                stringTagExtractor("main_fishing_gear")
            );

        final var marketGrid =
            new BiomassMarketGridFromPriceTableFactory(
                csvTableFromFile(inputPath.plus("prices.csv")),
                "date",
                "market_code",
                "species_code",
                "gear_code",
                "price",
                "currency",
                "measurement_unit",
                portGrid,
                species
            );

        final var optionValuesRegister =
            new ComponentRegisterFactory<MutableOptionValues<Int2D>>();

        final var optionValues =
            component(
                new ExponentialMovingAverageOptionValuesFactory<>(LEARNING_ALPHA),
                optionValuesRegister
            );

        final var totalAllowableCatchQuotas =
            scheduledRepeatingFromStart(
                DAILY,
                totalAllowableCatchQuotas(
                    fleetSegmentMapper(
                        "country_of_registration",
                        "loa",
                        vesselLengthClassMapper,
                        "Industrial",
                        "POSEIDON"
                    )
                )
            );

        final var readyForDeparture =
            checkThat(
                allOf(
                    isPermitted(
                        departNow(),
                        totalAllowableCatchQuotas
                    ),
                    condition(
                        currentTime(),
                        afterTime(time(21, 59, 59))
                    ),
                    condition(
                        currentDayOfWeek(),
                        in(setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY))
                    )
                )
            );

        final var waitUntilNextEvening =
            waitFor(
                durationUntil(
                    nextDayAtTime(time(22, 0, 0))
                )
            );

        final var tripEvaluator =
            tripEvaluator(
                optionValues,
                totalBiomassCaughtPerHour()
            );

        final var startTrip =
            startTrip(
                new EpsilonGreedyDestinationSupplierFactory(
                    EXPLORATION_PROBABILITY,
                    new NeighbourhoodGridExplorerFactory(
                        optionValues,
                        fishingLocationChecker,
                        pathFinder,
                        shiftedInt(randomPoisson(MEAN_EXPLORATION_RADIUS), 1)
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

        final var purseSeineDiscardRates =
            SpeciesSpecificDiscardRatesFactory.fromFile(
                INPUT_PATH.resolve("species.csv"),
                "species_code",
                "life_stage",
                species,
                DEFAULT_PURSE_SEINE_DISCARD_RATE
            );
        final var purseSeineDiscardMortalityRates =
            SpeciesSpecificDiscardMortalityRatesFactory.fromFile(
                INPUT_PATH.resolve("species.csv"),
                "species_code",
                "life_stage",
                species,
                DEFAULT_PURSE_SEINE_DISCARD_MORTALITY_RATE
            );
        final var bottomTrawlerDiscardRates =
            SpeciesSpecificDiscardRatesFactory.fromFile(
                INPUT_PATH.resolve("species.csv"),
                "species_code",
                "life_stage",
                species,
                DEFAULT_BOTTOM_TRAWLER_DISCARD_RATE
            );
        final var bottomTrawlerDiscardMortalityRates =
            SpeciesSpecificDiscardMortalityRatesFactory.fromFile(
                INPUT_PATH.resolve("species.csv"),
                "species_code",
                "life_stage",
                species,
                DEFAULT_BOTTOM_TRAWLER_DISCARD_MORTALITY_RATE
            );

        final var purseSeinerFishingTask = new FishingFactory(
            currentCellFisheable(
                fisheableBiomassGrids(biomassGrids)
            ),
            new CompositeDispositionProcessFactory<>(
                purseSeineDiscardRates,
                new ProportionallyLimitingBiomassToHoldFactory(),
                purseSeineDiscardMortalityRates
            )
        );
        final var bottomTrawlerFishingTask = new FishingFactory(
            currentCellFisheable(
                fisheableBiomassGrids(biomassGrids)
            ),
            new CompositeDispositionProcessFactory<>(
                bottomTrawlerDiscardRates,
                new ProportionallyLimitingBiomassToHoldFactory(),
                bottomTrawlerDiscardMortalityRates
            )
        );

        final var fuelStationGrid = fuelStationGrid(
            portGrid,
            oneFuelStationPerPort(
                portGrid,
                money(1.50, "EUR"),
                400
            )
        );

        final var purseSeinerBehaviour =
            new BehaviourFactory(
                sequenceTask(
                    new SucceedOrWaitTaskFactory(
                        sequenceTask(
                            readyForDeparture,
                            startTrip
                        ),
                        waitUntilNextEvening
                    ),
                    new TravelAlongPathFactory(pathFinder, distance),
                    purseSeinerFishingTask,
                    new SetDestinationToOriginFactory(),
                    new TravelAlongPathFactory(pathFinder, distance),
                    payTripCost(
                        composedFunction(
                            costsKeyFromVessel,
                            mapValueExtractor(hourlyCostsMap)
                        )
                    ),
                    landCatches(constant(hours(1))),
                    refuel(fuelStationGrid),
                    new EndTripFactory()
                )
            );

        final var bottomTrawlerBehaviour =
            new BehaviourFactory(
                sequenceTask(
                    new SucceedOrWaitTaskFactory(
                        sequenceTask(
                            readyForDeparture,
                            startTrip
                        ),
                        waitUntilNextEvening
                    ),
                    new TravelAlongPathFactory(pathFinder, distance),
                    bottomTrawlerFishingTask,
                    new SetDestinationToOriginFactory(),
                    new TravelAlongPathFactory(pathFinder, distance),
                    new LandCatchesFactory(constant(hours(1))),
                    refuel(fuelStationGrid),
                    new EndTripFactory()
                )
            );

        final var behaviour =
            VesselScopeFactoriesByCode
                .<Behaviour>builder()
                .factory(PURSE_SEINE_GEAR_CODE, purseSeinerBehaviour)
                .factory(BOTTOM_TRAWLER_GEAR_CODE, bottomTrawlerBehaviour)
                .defaultFactory(new InactiveBehaviourFactory())
                .build();

        final var fleet =
            FleetFromVesselRegisterFactory
                .builder()
                .fleet(new FleetFactory(vesselField, portGrid, marketGrid))
                .data(csvTableFromFile(inputPath.plus("fleet_register.csv")))
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
                .engine(
                    new SimpleEngineFactory<>(
                        // TODO: do we need a realistic value for tank volume?
                        fullTank(volumeOf(100_000, LITRE)),
                        speedOf(VESSEL_SPEED_IN_KNOTS, KNOT),
                        volumeOf(3, LITRE) // TODO: find realistic value here
                    )
                )
                .extraFactory(tripEvaluator)
                .build();

        final var fixedCostCollector =
            scheduledRepeatingFromStart(
                DAILY,
                fixedCostCollector(
                    fleet,
                    composedFunction(
                        costsKeyFromVessel,
                        mapValueExtractor(
                            mapFromTable(
                                csvTableFromFile(inputPath.plus("operating_costs.csv")),
                                costsKeyFromRow,
                                moneyFromRow("currency", "fixed_cost_per_day")
                            )
                        )
                    )
                )
            );

        final var directoryRemover =
            new FinalProcessFactory<>(
                directoryRemover(outputPath, false)
            );

        builder
            .startingDateTime(startOf(START_DATE))
            .component("species", species)
            .component("bathymetricGrid", bathymetricGrid)
            .component("carryingCapacityGrid", carryingCapacityGrid)
            .component("biomassGrids", biomassGrids)
            .component("marketGrid", marketGrid)
            .component("portGrid", portGrid)
            .component("regulations", regulations)
            .component("totalAllowableCatchQuotas", totalAllowableCatchQuotas)
            .component("vesselField", vesselField)
            .component("modelGrid", modelGrid)
            .component("monthlyProcesses", monthlyProcesses)
            .component("fleet", fleet)
            .component("fixedCostCollector", fixedCostCollector)
            .component("fishingActionAccumulator", fishingActionAccumulator)
            .component("biomassSaleAccumulator", biomassSaleAccumulator)
            .component("directoryRemover", directoryRemover);

        return builder.build();
    }
}
