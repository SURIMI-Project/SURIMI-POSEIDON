/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2024-2026, University of Oxford.
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

package eu.project.surimi.poseidon.scenarios.northwesternmed;

import sim.util.Int2D;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.agents.choices.MutableOptionValues;
import uk.ac.ox.poseidon.agents.components.VesselComponentRegisterFactory;
import uk.ac.ox.poseidon.agents.tasks.Behaviour;
import uk.ac.ox.poseidon.agents.vessels.FleetFromVesselRegisterFactory;
import uk.ac.ox.poseidon.agents.vessels.VesselScopeFactoriesByCode;
import uk.ac.ox.poseidon.agents.vessels.engines.Engine;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.schedule.TemporalSchedule;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.geography.grids.ModelGridFromGridFile;
import uk.ac.ox.poseidon.geography.ports.PortGrid;
import uk.ac.ox.poseidon.io.ScenarioWriter;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static eu.project.surimi.poseidon.regulations.Factories.*;
import static eu.project.surimi.poseidon.server.fleet.Factories.fleetSegmentMapper;
import static java.time.DayOfWeek.*;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static si.uom.NonSI.KNOT;
import static tech.units.indriya.unit.Units.KILOGRAM;
import static tech.units.indriya.unit.Units.LITRE;
import static uk.ac.ox.poseidon.agents.catches.Factories.catchCategory;
import static uk.ac.ox.poseidon.agents.catches.Factories.uniformCatchCategoriser;
import static uk.ac.ox.poseidon.agents.catches.disposition.Factories.*;
import static uk.ac.ox.poseidon.agents.choices.Factories.*;
import static uk.ac.ox.poseidon.agents.choices.evaluation.Factories.totalBiomassCaughtPerHour;
import static uk.ac.ox.poseidon.agents.choices.evaluation.Factories.tripEvaluator;
import static uk.ac.ox.poseidon.agents.components.Factories.registeredVesselComponent;
import static uk.ac.ox.poseidon.agents.components.Factories.vesselComponentRegister;
import static uk.ac.ox.poseidon.agents.fields.Factories.vesselField;
import static uk.ac.ox.poseidon.agents.fisheables.Factories.currentCellFisheable;
import static uk.ac.ox.poseidon.agents.market.Factories.biomassMarketGridFromPriceTable;
import static uk.ac.ox.poseidon.agents.market.Factories.biomassSaleAccumulator;
import static uk.ac.ox.poseidon.agents.money.Factories.moneyFromRow;
import static uk.ac.ox.poseidon.agents.regulations.actions.Factories.departNow;
import static uk.ac.ox.poseidon.agents.regulations.predicates.Factories.fishingLocationLegalityChecker;
import static uk.ac.ox.poseidon.agents.tasks.Factories.behaviour;
import static uk.ac.ox.poseidon.agents.tasks.Factories.inactiveBehaviour;
import static uk.ac.ox.poseidon.agents.tasks.accounting.Factories.payTripCost;
import static uk.ac.ox.poseidon.agents.tasks.branches.Factories.selectorTask;
import static uk.ac.ox.poseidon.agents.tasks.branches.Factories.sequenceTask;
import static uk.ac.ox.poseidon.agents.tasks.destinations.Factories.startTrip;
import static uk.ac.ox.poseidon.agents.tasks.fishing.Factories.fishing;
import static uk.ac.ox.poseidon.agents.tasks.fishing.Factories.fishingEventAccumulator;
import static uk.ac.ox.poseidon.agents.tasks.general.Factories.*;
import static uk.ac.ox.poseidon.agents.tasks.landings.Factories.landCatches;
import static uk.ac.ox.poseidon.agents.tasks.travel.Factories.*;
import static uk.ac.ox.poseidon.agents.vessels.Factories.fleet;
import static uk.ac.ox.poseidon.agents.vessels.accounts.Factories.fixedCostCollector;
import static uk.ac.ox.poseidon.agents.vessels.engines.Factories.infiniteTank;
import static uk.ac.ox.poseidon.agents.vessels.engines.Factories.simpleEngine;
import static uk.ac.ox.poseidon.agents.vessels.extractors.tags.Factories.doubleTagExtractor;
import static uk.ac.ox.poseidon.agents.vessels.extractors.tags.Factories.stringTagExtractor;
import static uk.ac.ox.poseidon.agents.vessels.friends.Factories.dynamicFriendsSupplier;
import static uk.ac.ox.poseidon.agents.vessels.gears.Factories.inactiveGear;
import static uk.ac.ox.poseidon.agents.vessels.gears.Factories.indexedBiomassCatchabilityGear;
import static uk.ac.ox.poseidon.agents.vessels.holds.Factories.infiniteBiomassHold;
import static uk.ac.ox.poseidon.agents.vessels.predicates.Factories.*;
import static uk.ac.ox.poseidon.agents.vessels.providers.Factories.*;
import static uk.ac.ox.poseidon.biology.biomass.Factories.*;
import static uk.ac.ox.poseidon.biology.species.Factories.speciesFromData;
import static uk.ac.ox.poseidon.biology.species.extractors.Factories.*;
import static uk.ac.ox.poseidon.core.aggregators.Factories.maxAggregator;
import static uk.ac.ox.poseidon.core.events.Factories.eventClearer;
import static uk.ac.ox.poseidon.core.functions.Factories.*;
import static uk.ac.ox.poseidon.core.functions.NumericIntervalToStringMapperFactory.interval;
import static uk.ac.ox.poseidon.core.predicates.Factories.condition;
import static uk.ac.ox.poseidon.core.predicates.Factories.in;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.allOf;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.anyOf;
import static uk.ac.ox.poseidon.core.predicates.numeric.Factories.greaterThan;
import static uk.ac.ox.poseidon.core.predicates.numeric.Factories.lessThan;
import static uk.ac.ox.poseidon.core.predicates.temporal.Factories.afterTime;
import static uk.ac.ox.poseidon.core.providers.Factories.shiftedInt;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.*;
import static uk.ac.ox.poseidon.core.providers.math.Factories.maxInt;
import static uk.ac.ox.poseidon.core.providers.math.Factories.minInt;
import static uk.ac.ox.poseidon.core.providers.random.Factories.randomPoisson;
import static uk.ac.ox.poseidon.core.providers.temporal.Factories.*;
import static uk.ac.ox.poseidon.core.quantities.Factories.*;
import static uk.ac.ox.poseidon.core.schedule.Factories.*;
import static uk.ac.ox.poseidon.core.time.Factories.*;
import static uk.ac.ox.poseidon.core.utils.Factories.*;
import static uk.ac.ox.poseidon.core.utils.Utils.multiStringKey;
import static uk.ac.ox.poseidon.geography.allocators.Factories.filteredAllocator;
import static uk.ac.ox.poseidon.geography.allocators.Factories.supplierAllocator;
import static uk.ac.ox.poseidon.geography.bathymetry.Factories.bathymetricGridFromGridFile;
import static uk.ac.ox.poseidon.geography.distance.Factories.haversineDistanceCalculator;
import static uk.ac.ox.poseidon.geography.grids.Factories.*;
import static uk.ac.ox.poseidon.geography.grids.extractors.Factories.cellValue;
import static uk.ac.ox.poseidon.geography.paths.Factories.pathFinder;
import static uk.ac.ox.poseidon.geography.ports.Factories.portGrid;
import static uk.ac.ox.poseidon.geography.ports.Factories.portsFromTable;
import static uk.ac.ox.poseidon.geography.predicates.Factories.isActiveWaterCell;
import static uk.ac.ox.poseidon.io.Factories.directoryRemover;
import static uk.ac.ox.poseidon.io.paths.Factories.path;
import static uk.ac.ox.poseidon.io.paths.Factories.simulationFolder;
import static uk.ac.ox.poseidon.io.tables.Factories.*;
import static uk.ac.ox.poseidon.regulations.Factories.forbiddenIf;
import static uk.ac.ox.poseidon.regulations.predicates.Factories.isPermitted;
import static uk.ac.ox.poseidon.regulations.predicates.spatial.Factories.actionCellPredicate;

public class NorthwesternMedScenario implements Supplier<Scenario> {

    public static final LocalDate START_DATE = LocalDate.of(2013, 1, 1);
    static final Path INPUT_PATH = Path.of("inputs", "northwestern_med");
    private static final double LEARNING_ALPHA = 1;
    private static final double PURSE_SEINER_EXPLORATION_PROBABILITY = 0.2;
    private static final double BOTTOM_TRAWLER_EXPLORATION_PROBABILITY = 0.2;
    private static final int PURSE_SEINER_MEAN_EXPLORATION_RADIUS = 1;
    private static final int BOTTOM_TRAWLER_MEAN_EXPLORATION_RADIUS = 1;
    private static final double DEFAULT_CATCH_PROPORTION = 0.1;
    private static final double PURSE_SEINER_MINIMUM_DEPTH_THRESHOLD = -35.0;
    private static final double BOTTOM_TRAWLER_MINIMUM_DEPTH_THRESHOLD = -50.0;
    private static final double BOTTOM_TRAWLER_MAXIMUM_DEPTH_THRESHOLD = -1000.0;
    private static final double PURSE_SEINER_VESSEL_SPEED_IN_KNOTS = 9.5; // as per email on 2025-03-18 08:20
    private static final double BOTTOM_TRAWLER_CRUISING_SPEED_IN_KNOTS = 9; // midpoint of 8-10 knots, per bottom trawler regulation doc
    private static final double BOTTOM_TRAWLER_TRAWLING_SPEED_IN_KNOTS = 3; // midpoint of 2-4 knots, per bottom trawler regulation doc
    private static final String PURSE_SEINE_GEAR_CODE = "PS";
    private static final String BOTTOM_TRAWLER_GEAR_CODE = "OTB";
    private static final String CATCH_CATEGORY = "Fresh - Whole";

    static void main(final String[] args) {
        final int numSteps = 12 * 10;
        final Period stepSize = Period.ofMonths(1);
        final Scenario scenario = new NorthwesternMedScenario().get();
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
            modelGridWithActiveCells(
                new ModelGridFromGridFile<>(bathymetricGridPath),
                cellSetFromGridFile(
                    inputPath.plus("exclusion_grid.asc"),
                    0
                )
            );

        final var bathymetricGrid =
            bathymetricGridFromGridFile(
                bathymetricGridPath,
                modelGrid,
                maxAggregator(),
                false
            );

        final var mpaGrids =
            staticGridsFromNetCdf(
                modelGrid,
                staticNetCdfGridReader(
                    inputPath.plus("mpa_grids.nc"),
                    "latitude",
                    "longitude"
                )
            );

        final var habitatGrids =
            staticGridsFromNetCdf(
                modelGrid,
                staticNetCdfGridReader(
                    inputPath.plus("habitat_grids.nc"),
                    "latitude",
                    "longitude"
                )
            );

        final var rockGrid = mapEntry(habitatGrids, "rock");
        final var posidoniaGrid = mapEntry(habitatGrids, "posidonia");
        final var cymodoceaGrid = mapEntry(habitatGrids, "cymodocea");

        final var mpaClosedMonths =
            mpaClosedMonths(
                tableFromCsvFile(inputPath.plus("mpa_months.csv")),
                "mpa_id",
                "month",
                "closed"
            );

        final var mpaFleetRestrictions =
            mpaFleetRestrictions(
                tableFromCsvFile(inputPath.plus("mpa_fleet_restrictions.csv")),
                "mpa_id",
                "gear_code",
                "country_code"
            );

        final var portClosures =
            portClosures(
                tableFromCsvFile(inputPath.plus("port_closures.csv")),
                "port_code",
                "gear_code",
                "start_date",
                "end_date"
            );

        final var commonActionPredicate =
            anyOf(
                actionCellPredicate(
                    modelGrid,
                    in(
                        constant(
                            cellSetFromGridFile(
                                inputPath.plus("french_eez.asc"),
                                1
                            )
                        )
                    )
                ),
                mpaClosurePredicate(
                    modelGrid,
                    mpaGrids,
                    mpaClosedMonths,
                    mpaFleetRestrictions,
                    "country_of_registration"
                ),
                portClosurePredicate(portClosures),
                actionCellPredicate(
                    modelGrid,
                    condition(
                        cellValue(rockGrid),
                        greaterThan(0.0)
                    )
                ),
                actionCellPredicate(
                    modelGrid,
                    condition(
                        cellValue(posidoniaGrid),
                        greaterThan(0.0)
                    )
                ),
                actionCellPredicate(
                    modelGrid,
                    condition(
                        cellValue(cymodoceaGrid),
                        greaterThan(0.0)
                    )
                )
            );

        final var purseSeinerRegulations =
            forbiddenIf(
                anyOf(
                    actionCellPredicate(
                        modelGrid,
                        condition(
                            cellValue(bathymetricGrid),
                            greaterThan(PURSE_SEINER_MINIMUM_DEPTH_THRESHOLD)
                        )
                    ),
                    commonActionPredicate
                )
            );

        final var bottomTrawlerRegulations =
            forbiddenIf(
                anyOf(
                    actionCellPredicate(
                        modelGrid,
                        condition(
                            cellValue(bathymetricGrid),
                            greaterThan(BOTTOM_TRAWLER_MINIMUM_DEPTH_THRESHOLD)
                        )
                    ),
                    actionCellPredicate(
                        modelGrid,
                        condition(
                            cellValue(bathymetricGrid),
                            lessThan(BOTTOM_TRAWLER_MAXIMUM_DEPTH_THRESHOLD)
                        )
                    ),
                    commonActionPredicate
                )
            );

        final var vesselField =
            vesselField(modelGrid);

        final var distance =
            haversineDistanceCalculator(modelGrid);

        final Factory<Scope, ? extends PortGrid> portGrid =
            portGrid(
                portsFromTable(
                    tableFromCsvFile(inputPath.plus("ports.csv")),
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

        final var purseSeinerFishingLocationChecker =
            fishingLocationLegalityChecker(
                purseSeinerRegulations,
                pathFinder,
                distance
            );

        final var bottomTrawlerFishingLocationChecker =
            fishingLocationLegalityChecker(
                bottomTrawlerRegulations,
                pathFinder,
                distance
            );

        final var species =
            speciesFromData(
                tableFromCsvFile(inputPath.plus("species.csv")),
                "species_code",
                "species_name",
                "life_stage"
            );

        final var catchabilityMapsByGearCode =
            Table
                .read()
                .csv(INPUT_PATH.resolve("discard_ratios.csv").toFile())
                .stream()
                .collect(groupingBy(
                    row -> row.getString("gear_code"),
                    LinkedHashMap::new,
                    toMap(
                        row -> multiStringKey(
                            row.getString("species_code"),
                            row.getString("life_stage")
                        ),
                        _ -> DEFAULT_CATCH_PROPORTION,
                        (a, _) -> a,
                        LinkedHashMap::new
                    )
                ));

        final var purseSeinerCatchabilities =
            perSimulation(object(checkNotNull(
                catchabilityMapsByGearCode.get(PURSE_SEINE_GEAR_CODE)
            )));

        final var bottomTrawlerCatchabilities =
            perSimulation(object(checkNotNull(
                catchabilityMapsByGearCode.get(BOTTOM_TRAWLER_GEAR_CODE)
            )));

        final var fishingGear =
            VesselScopeFactoriesByCode.<Gear>builder()
                .factory(
                    PURSE_SEINE_GEAR_CODE,
                    indexedBiomassCatchabilityGear(
                        PURSE_SEINE_GEAR_CODE,
                        constant(hours(1)),
                        species,
                        defaultIfNull(
                            composedFunction(
                                speciesKey(),
                                mapValueExtractor(purseSeinerCatchabilities)
                            ),
                            0.0
                        ),
                        massOf(1, KILOGRAM)
                    )
                )
                .factory(
                    BOTTOM_TRAWLER_GEAR_CODE,
                    indexedBiomassCatchabilityGear(
                        BOTTOM_TRAWLER_GEAR_CODE,
                        constant(hours(1)),
                        species,
                        defaultIfNull(
                            composedFunction(
                                speciesKey(),
                                mapValueExtractor(bottomTrawlerCatchabilities)
                            ),
                            0.0
                        ),
                        massOf(1, KILOGRAM)
                    )
                )
                .defaultFactory(inactiveGear(null))
                .build();

        final var zeroBiomassAllocator =
            filteredAllocator(
                supplierAllocator(constantDouble(0.0)),
                isActiveWaterCell(bathymetricGrid)
            );

        final var biomassGrids =
            biomassGrids(
                modelGrid,
                species,
                listOf(zeroBiomassAllocator)
            );

        final var fisheableBiomassGrids =
            fisheableBiomassGrids(biomassGrids);

        final var timeIndexedBiomassGrids =
            timeIndexedBiomassGridsFromNetCdf(
                modelGrid,
                species,
                inputPath.plus("biomass_grids.nc")
            );

        final var timeIndexedBiomassGridUpdates =
            timeIndexedBiomassGridUpdates(
                fisheableBiomassGrids,
                timeIndexedBiomassGrids
            );

        final var biomassSaleAccumulator =
            biomassSaleAccumulator();

        final var fishingActionAccumulator =
            fishingEventAccumulator();

        final var monthlyProcesses =
            scheduledRepeating(
                dateTimeAfterStarting(ONE_MONTH),
                MONTHLY,
                steppableSequence(
                    listOf(
                        eventClearer(biomassSaleAccumulator),
                        eventClearer(fishingActionAccumulator)
                    )
                ),
                -2
            );

        final var costsKeyFromRow =
            multiStringKeyFromRow("country_code", "year", "vessel_length", "gear_code");

        final var hourlyCostsMap =
            mapFromTable(
                tableFromCsvFile(inputPath.plus("operating_costs.csv")),
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
            multiStringKeyFromFunctions(
                stringTagExtractor("country_of_registration"),
                minInt(maxInt(currentYear(), constantInt(2013)), constantInt(2023)),
                composedFunction(
                    doubleTagExtractor("loa"),
                    vesselLengthClassMapper
                ),
                stringTagExtractor("main_fishing_gear")
            );

        final var marketGrid =
            biomassMarketGridFromPriceTable(
                tableFromCsvFile(inputPath.plus("prices.csv")),
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

        final VesselComponentRegisterFactory<MutableOptionValues<Int2D>>
            optionValuesRegister = vesselComponentRegister();

        final var optionValues =
            registeredVesselComponent(
                exponentialMovingAverageOptionValues(LEARNING_ALPHA),
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

        final var purseSeinerReadyForDeparture =
            checkThat(
                allOf(
                    isPermitted(
                        departNow(),
                        totalAllowableCatchQuotas
                    ),
                    condition(
                        currentTime(),
                        afterTime(constant(time(21, 59, 59)))
                    ),
                    condition(
                        currentDayOfWeek(),
                        in(constant(setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY)))
                    )
                )
            );

        final var purseSeinerWaitUntilNextCheckpoint =
            waitFor(
                durationUntil(
                    nextTimeAt(time(6, 0, 0), time(22, 0, 0))
                )
            );

        // Bottom trawlers depart in the morning and return the same day (per Miquel Ortega:
        // ~07:00 departure, ~16:30-18:00 return), unlike purse seiners' overnight schedule
        // above - this used to share the purse seiner's 06:00/22:00 checkpoint pair, which
        // made trawlers only eligible to depart after 22:00.
        final var bottomTrawlerReadyForDeparture =
            checkThat(
                allOf(
                    isPermitted(
                        departNow(),
                        totalAllowableCatchQuotas
                    ),
                    condition(
                        currentTime(),
                        afterTime(constant(time(6, 59, 59)))
                    ),
                    condition(
                        currentDayOfWeek(),
                        in(constant(setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY)))
                    )
                )
            );

        final var bottomTrawlerWaitUntilNextCheckpoint =
            waitFor(
                durationUntil(
                    nextTimeAt(time(7, 0, 0))
                )
            );

        final var tripEvaluator =
            tripEvaluator(
                optionValues,
                totalBiomassCaughtPerHour()
            );

        final var bestOptionsFromFriends =
            bestOptionsFromFriends(
                optionValuesRegister,
                dynamicFriendsSupplier(
                    5,
                    optionValuesRegister,
                    allOf(
                        vesselIsActive(),
                        vesselHasSameHomePort()
                    )
                )
            );

        final var purseSeinerStartTrip =
            startTrip(
                epsilonGreedyDestination(
                    PURSE_SEINER_EXPLORATION_PROBABILITY,
                    neighbourhoodGridExplorer(
                        optionValues,
                        pathFinder,
                        purseSeinerFishingLocationChecker,
                        shiftedInt(randomPoisson(PURSE_SEINER_MEAN_EXPLORATION_RADIUS), 1),
                        currentCell()
                    ),
                    imitatingPicker(
                        optionValues,
                        purseSeinerFishingLocationChecker,
                        bestOptionsFromFriends
                    )
                )
            );

        final var bottomTrawlerStartTrip =
            startTrip(
                epsilonGreedyDestination(
                    BOTTOM_TRAWLER_EXPLORATION_PROBABILITY,
                    neighbourhoodGridExplorer(
                        optionValues,
                        pathFinder,
                        bottomTrawlerFishingLocationChecker,
                        shiftedInt(randomPoisson(BOTTOM_TRAWLER_MEAN_EXPLORATION_RADIUS), 1),
                        currentCell()
                    ),
                    imitatingPicker(
                        optionValues,
                        bottomTrawlerFishingLocationChecker,
                        bestOptionsFromFriends
                    )
                )
            );

        final var discardRatiosTable =
            tableFromCsvFile(inputPath.plus("discard_ratios.csv"));
        final var discardRatiosKeyFromRow =
            multiStringKeyFromRow("species_code", "life_stage", "gear_code");

        final var purseSeineDiscardRates =
            discardRates(
                species,
                defaultIfNull(
                    tableLookup(
                        multiStringKeyFromFunctions(
                            speciesCode(),
                            speciesLifeStage(),
                            constant(PURSE_SEINE_GEAR_CODE)
                        ),
                        discardRatiosTable,
                        discardRatiosKeyFromRow,
                        doubleFromRow("discard_ratio")
                    ),
                    0.0
                )
            );
        final var bottomTrawlerDiscardRates =
            discardRates(
                species,
                defaultIfNull(
                    tableLookup(
                        multiStringKeyFromFunctions(
                            speciesCode(),
                            speciesLifeStage(),
                            constant(BOTTOM_TRAWLER_GEAR_CODE)
                        ),
                        discardRatiosTable,
                        discardRatiosKeyFromRow,
                        doubleFromRow("discard_ratio")
                    ),
                    0.0
                )
            );

        final var discardMortality = fullDiscardMortality();

        final var purseSeinerFishingTask = fishing(
            currentCellFisheable(
                fisheableBiomassGrids
            ),
            compositeDispositionProcess(
                purseSeineDiscardRates,
                proportionallyLimitingBiomassToHold(),
                discardMortality
            )
        );
        final var bottomTrawlerFishingTask = fishing(
            currentCellFisheable(
                fisheableBiomassGrids
            ),
            compositeDispositionProcess(
                bottomTrawlerDiscardRates,
                proportionallyLimitingBiomassToHold(),
                discardMortality
            )
        );

        final var purseSeinerBehaviour =
            behaviour(
                sequenceTask(
                    selectorTask(
                        checkThat(vesselIsAt(homePortCell())),
                        travelAlongPathTo(
                            pathFinder,
                            distance,
                            homePortCell(),
                            vesselEventManager()
                        )
                    ),
                    succeedOrWait(
                        sequenceTask(
                            purseSeinerReadyForDeparture,
                            purseSeinerStartTrip
                        ),
                        purseSeinerWaitUntilNextCheckpoint
                    ),
                    travelAlongPathTo(
                        pathFinder,
                        distance,
                        currentTripDestinationCell(),
                        currentTripEventManager()
                    ),
                    purseSeinerFishingTask,
                    setDestinationToOrigin(),
                    travelAlongPathTo(
                        pathFinder,
                        distance,
                        currentTripDestinationCell(),
                        currentTripEventManager()
                    ),
                    payTripCost(
                        composedFunction(
                            costsKeyFromVessel,
                            mapValueExtractor(hourlyCostsMap)
                        )
                    ),
                    landCatches(constant(hours(1))),
                    endTrip()
                )
            );

        final var bottomTrawlerBehaviour =
            behaviour(
                sequenceTask(
                    selectorTask(
                        checkThat(vesselIsAt(homePortCell())),
                        travelAlongPathTo(
                            pathFinder,
                            distance,
                            homePortCell(),
                            vesselEventManager()
                        )
                    ),
                    succeedOrWait(
                        sequenceTask(
                            bottomTrawlerReadyForDeparture,
                            bottomTrawlerStartTrip
                        ),
                        bottomTrawlerWaitUntilNextCheckpoint
                    ),
                    travelAlongPathTo(
                        pathFinder,
                        distance,
                        currentTripDestinationCell(),
                        currentTripEventManager()
                    ),
                    bottomTrawlerFishingTask,
                    setDestinationToOrigin(),
                    travelAlongPathTo(
                        pathFinder,
                        distance,
                        currentTripDestinationCell(),
                        currentTripEventManager()
                    ),
                    landCatches(constant(hours(1))),
                    endTrip()
                )
            );

        final var behaviour =
            VesselScopeFactoriesByCode
                .<Behaviour>builder()
                .factory(PURSE_SEINE_GEAR_CODE, purseSeinerBehaviour)
                .factory(BOTTOM_TRAWLER_GEAR_CODE, bottomTrawlerBehaviour)
                .defaultFactory(inactiveBehaviour())
                .build();

        final var engine =
            VesselScopeFactoriesByCode
                .<Engine>builder()
                .factory(
                    PURSE_SEINE_GEAR_CODE,
                    simpleEngine(
                        infiniteTank(),
                        speedOf(PURSE_SEINER_VESSEL_SPEED_IN_KNOTS, KNOT),
                        volumeOf(0, LITRE) // we don't consume fuel from our infinite tank
                    )
                )
                .factory(
                    BOTTOM_TRAWLER_GEAR_CODE,
                    simpleEngine(
                        infiniteTank(),
                        speedOf(BOTTOM_TRAWLER_CRUISING_SPEED_IN_KNOTS, KNOT),
                        volumeOf(0, LITRE) // we don't consume fuel from our infinite tank
                    )
                )
                .defaultFactory(
                    simpleEngine(
                        infiniteTank(),
                        speedOf(0, KNOT), // inactive vessels never travel, so this is never read
                        volumeOf(0, LITRE) // we don't consume fuel from our infinite tank
                    )
                )
                .build();

        final var fleet =
            FleetFromVesselRegisterFactory
                .builder()
                .fleet(fleet(vesselField, portGrid, marketGrid))
                .data(tableFromCsvFile(inputPath.plus("fleet_register.csv")))
                .behaviour(behaviour)
                .dataMapping("behaviour.code", "main_fishing_gear")
                .hold(
                    infiniteBiomassHold(
                        uniformCatchCategoriser(catchCategory(CATCH_CATEGORY))
                    )
                )
                .gear(fishingGear)
                .dataMapping("gear.code", "main_fishing_gear")
                .dataMapping("gear.defaultFactory.code", "main_fishing_gear")
                .engine(engine)
                .dataMapping("engine.code", "main_fishing_gear")
                .extraFactory(tripEvaluator)
                .build();

        final var fixedCostCollector =
            scheduledRepeatingFromStart(
                DAILY,
                fixedCostCollector(
                    fleet,
                    tableLookup(
                        costsKeyFromVessel,
                        tableFromCsvFile(inputPath.plus("operating_costs.csv")),
                        costsKeyFromRow,
                        moneyFromRow("currency", "fixed_cost_per_day")
                    )
                )
            );

        final var directoryRemover =
            finalProcess(
                directoryRemover(outputPath, false)
            );

        builder
            .startingDateTime(startOf(START_DATE))
            .component("purseSeinerCatchabilities", purseSeinerCatchabilities)
            .component("bottomTrawlerCatchabilities", bottomTrawlerCatchabilities)
            .component("species", species)
            .component("bathymetricGrid", bathymetricGrid)
            .component("biomassGrids", biomassGrids)
            .component("timeIndexedBiomassGridUpdates", timeIndexedBiomassGridUpdates)
            .component("marketGrid", marketGrid)
            .component("portGrid", portGrid)
            .component("purseSeinerRegulations", purseSeinerRegulations)
            .component("bottomTrawlerRegulations", bottomTrawlerRegulations)
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
