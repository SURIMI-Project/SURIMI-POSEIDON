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

import static com.google.common.base.Preconditions.checkNotNull;
import static eu.project.surimi.poseidon.regulations.Factories.*;
import static eu.project.surimi.poseidon.server.fleet.Factories.fleetSegmentMapper;
import static java.time.DayOfWeek.*;
import static java.time.Period.ofDays;
import static java.time.Period.ofMonths;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static si.uom.NonSI.KNOT;
import static tech.units.indriya.unit.Units.KILOGRAM;
import static tech.units.indriya.unit.Units.LITRE;
import static uk.ac.ox.poseidon.biology.biomass.Factories.*;
import static uk.ac.ox.poseidon.biology.species.Factories.speciesFromData;
import static uk.ac.ox.poseidon.biology.species.extractors.Factories.speciesProperty;
import static uk.ac.ox.poseidon.core.aggregators.Factories.maxAggregator;
import static uk.ac.ox.poseidon.core.events.Factories.eventClearer;
import static uk.ac.ox.poseidon.core.functions.Factories.*;
import static uk.ac.ox.poseidon.core.functions.IntervalLabelFactory.interval;
import static uk.ac.ox.poseidon.core.predicates.Factories.condition;
import static uk.ac.ox.poseidon.core.predicates.Factories.in;
import static uk.ac.ox.poseidon.core.predicates.comparable.Factories.greaterThan;
import static uk.ac.ox.poseidon.core.predicates.comparable.Factories.lessThan;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.allOf;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.anyOf;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.not;
import static uk.ac.ox.poseidon.core.predicates.temporal.Factories.afterTime;
import static uk.ac.ox.poseidon.core.providers.Factories.shiftedInt;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.always;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.constant;
import static uk.ac.ox.poseidon.core.providers.math.Factories.maxInt;
import static uk.ac.ox.poseidon.core.providers.math.Factories.minInt;
import static uk.ac.ox.poseidon.core.providers.random.Factories.randomPoisson;
import static uk.ac.ox.poseidon.core.providers.temporal.Factories.*;
import static uk.ac.ox.poseidon.core.quantities.Factories.*;
import static uk.ac.ox.poseidon.core.schedule.Factories.*;
import static uk.ac.ox.poseidon.core.time.Factories.*;
import static uk.ac.ox.poseidon.core.utils.Factories.*;
import static uk.ac.ox.poseidon.core.utils.Utils.multiStringKey;
import static uk.ac.ox.poseidon.fishing.catches.Factories.catchCategory;
import static uk.ac.ox.poseidon.fishing.catches.Factories.uniformCatchCategoriser;
import static uk.ac.ox.poseidon.fishing.catches.disposition.Factories.*;
import static uk.ac.ox.poseidon.fishing.choices.Factories.*;
import static uk.ac.ox.poseidon.fishing.fields.Factories.vesselField;
import static uk.ac.ox.poseidon.fishing.fisheables.Factories.currentCellFisheable;
import static uk.ac.ox.poseidon.fishing.market.Factories.biomassSaleAccumulator;
import static uk.ac.ox.poseidon.fishing.market.Factories.homePortCatchValuation;
import static uk.ac.ox.poseidon.fishing.market.Factories.marketGrid;
import static uk.ac.ox.poseidon.fishing.market.Factories.oneBiomassMarketPerPort;
import static uk.ac.ox.poseidon.fishing.money.Factories.moneyFromRow;
import static uk.ac.ox.poseidon.fishing.regulations.actions.Factories.departNow;
import static uk.ac.ox.poseidon.fishing.regulations.predicates.Factories.fishingLocationLegalityChecker;
import static uk.ac.ox.poseidon.fishing.social.Factories.*;
import static uk.ac.ox.poseidon.fishing.tasks.accounting.Factories.payTripCost;
import static uk.ac.ox.poseidon.fishing.tasks.branches.Factories.selectorTask;
import static uk.ac.ox.poseidon.fishing.tasks.branches.Factories.sequenceTask;
import static uk.ac.ox.poseidon.fishing.tasks.decorators.Factories.untilFail;
import static uk.ac.ox.poseidon.fishing.tasks.destinations.Factories.chooseNextDestination;
import static uk.ac.ox.poseidon.fishing.tasks.fishing.Factories.fishing;
import static uk.ac.ox.poseidon.fishing.tasks.fishing.Factories.fishingEventAccumulator;
import static uk.ac.ox.poseidon.fishing.tasks.general.Factories.*;
import static uk.ac.ox.poseidon.fishing.tasks.landings.Factories.landCatches;
import static uk.ac.ox.poseidon.fishing.tasks.travel.Factories.*;
import static uk.ac.ox.poseidon.fishing.travel.Factories.routeProperty;
import static uk.ac.ox.poseidon.fishing.travel.Factories.routeViaDestination;
import static uk.ac.ox.poseidon.fishing.travel.Factories.timeCost;
import static uk.ac.ox.poseidon.fishing.travel.Factories.travelCost;
import static uk.ac.ox.poseidon.fishing.trips.Factories.cellWasFishedThisTrip;
import static uk.ac.ox.poseidon.fishing.vessels.Factories.*;
import static uk.ac.ox.poseidon.fishing.vessels.accounts.Factories.fixedCostCollector;
import static uk.ac.ox.poseidon.fishing.vessels.engines.Factories.simpleEngine;
import static uk.ac.ox.poseidon.fishing.vessels.gears.Factories.indexedBiomassCatchabilityGear;
import static uk.ac.ox.poseidon.fishing.vessels.holds.Factories.infiniteBiomassHold;
import static uk.ac.ox.poseidon.fishing.vessels.tanks.Factories.infiniteTank;
import static uk.ac.ox.poseidon.fishing.vessels.values.Factories.*;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.doubleAttribute;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.stringAttribute;
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
import static uk.ac.ox.poseidon.regulations.Factories.allOf;
import static uk.ac.ox.poseidon.regulations.Factories.forbiddenIf;
import static uk.ac.ox.poseidon.regulations.predicates.Factories.isPermitted;
import static uk.ac.ox.poseidon.regulations.predicates.spatial.Factories.actionCellPredicate;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
import sim.util.Int2D;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.biology.buckets.Bucket;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.schedule.TemporalSchedule;
import uk.ac.ox.poseidon.fishing.choices.KeyedMemoryFactory;

public class NorthwesternMedScenario implements Supplier<Scenario> {

  public static final LocalDate START_DATE = LocalDate.of(2013, 1, 1);
  static final Path INPUT_PATH = Path.of("inputs", "northwestern_med");
  private static final double OWN_LEARNING_ALPHA = 0.5;
  private static final double SOCIAL_LEARNING_ALPHA = 0.2;
  private static final int MAXIMUM_CLIQUE_SIZE = 5;
  private static final double PURSE_SEINER_EXPLORATION_PROBABILITY = 0.2;
  private static final double BOTTOM_TRAWLER_EXPLORATION_PROBABILITY = 0.2;
  private static final int PURSE_SEINER_MEAN_EXPLORATION_RADIUS = 1;
  private static final int BOTTOM_TRAWLER_MEAN_EXPLORATION_RADIUS = 1;
  // Beyond the initial radius. In January 2013 every search that found a legal
  // cell only after widening found it within three cells; most that widen find
  // nothing legal anywhere.
  private static final int MAXIMUM_EXPLORATION_WIDENING = 3;
  // Rough values, rescaled against target_landings.csv, until catchability is
  // calibrated.
  private static final Path PLACEHOLDER_CATCHABILITIES_PATH = Path.of(
    "scenarios",
    "northwestern_med_placeholder_catchabilities.csv"
  );
  private static final double PURSE_SEINER_MINIMUM_DEPTH_THRESHOLD = -35.0;
  private static final double BOTTOM_TRAWLER_MINIMUM_DEPTH_THRESHOLD = -50.0;
  private static final double BOTTOM_TRAWLER_MAXIMUM_DEPTH_THRESHOLD = -1000.0;
  private static final double PURSE_SEINER_VESSEL_SPEED_IN_KNOTS = 9.5;
  // as per email on 2025-03-18 08:20
  private static final double BOTTOM_TRAWLER_CRUISING_SPEED_IN_KNOTS = 9;
  // midpoint of 8-10 knots, per bottom trawler regulation doc
  private static final double BOTTOM_TRAWLER_TRAWLING_SPEED_IN_KNOTS = 3;
  // midpoint of 2-4 knots, per bottom trawler regulation doc
  static final String PURSE_SEINE_GEAR_CODE = "PS";
  static final String BOTTOM_TRAWLER_GEAR_CODE = "OTB";
  private static final String CATCH_CATEGORY = "Fresh - Whole";

  /**
   * Runs a 10-year simulation of this scenario, printing the date-time reached
   * at each step, as a sanity check. To regenerate
   * {@code scenarios/northwestern_med.yaml}, use the
   * {@code writeNorthwesternMedScenario} Gradle task instead.
   */
  static void main(final String[] args) {
    final int numSteps = 12 * 10;
    final Period stepSize = Period.ofMonths(1);
    final Scenario scenario = new NorthwesternMedScenario().get();
    final Simulation simulation = scenario.startNewSimulation();
    final TemporalSchedule temporalSchedule = simulation.getTemporalSchedule();
    range(0, numSteps).forEach(_ -> {
      temporalSchedule.stepFor(simulation, stepSize);
      System.out.println(temporalSchedule.getDateTime());
    });
    simulation.finish();
  }

  /**
   * @return a freshly built Northwestern Mediterranean {@link Scenario}.
   */
  @Override
  public Scenario get() {
    final Scenario.ScenarioBuilder builder = Scenario.builder();

    final var purseSeinerReturnTime = time(6, 30, 0);
    // A set's typical duration. Fishers can't know how long a given set will
    // take, so the checks that a set fits (before the return time, within the
    // trip's length) and is legal plan with this; the gear's sets last this
    // long too, for now.
    final var purseSeineSetDuration = hours(1);
    final var bottomTrawlerSetDuration = hours(3);
    final var bottomTrawlerMaximumTripDuration = hours(12);

    final var inputPath = path(INPUT_PATH);
    final var outputPath = simulationFolder(path("outputs"));
    final var bathymetricGridPath = inputPath.plus("bathymetry_grid.asc");

    final var allCellsGrid = modelGridFromGridFile(bathymetricGridPath);
    final var modelGrid = modelGridWithActiveCells(
      allCellsGrid,
      cellSetFromGridFile(inputPath.plus("exclusion_grid.asc"), allCellsGrid, 0)
    );

    final var bathymetricGrid = bathymetricGridFromGridFile(
      bathymetricGridPath,
      modelGrid,
      maxAggregator(),
      false
    );

    final var mpaGrids = staticGridsFromNetCdf(
      modelGrid,
      staticNetCdfGridReader(
        inputPath.plus("mpa_grids.nc"),
        "latitude",
        "longitude"
      )
    );

    final var habitatGrids = staticGridsFromNetCdf(
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

    final var mpaClosedMonths = mpaClosedMonths(
      tableFromCsvFile(inputPath.plus("mpa_months.csv")),
      "mpa_id",
      "month",
      "closed"
    );

    final var mpaFleetRestrictions = mpaFleetRestrictions(
      tableFromCsvFile(inputPath.plus("mpa_fleet_restrictions.csv")),
      "mpa_id",
      "gear_code",
      "country_code"
    );

    final var portClosures = portClosures(
      tableFromCsvFile(inputPath.plus("port_closures.csv")),
      "port_code",
      "gear_code",
      "year",
      "start_date",
      "end_date"
    );

    final var commonActionPredicate = anyOf(
      actionCellPredicate(
        modelGrid,
        in(
          constant(
            cellSetFromGridFile(inputPath.plus("french_eez.asc"), modelGrid, 1)
          )
        )
      ),
      mpaClosurePredicate(
        modelGrid,
        mpaGrids,
        mpaClosedMonths,
        mpaFleetRestrictions,
        stringAttribute("main_fishing_gear"),
        stringAttribute("country_of_registration")
      ),
      portClosurePredicate(
        portClosures,
        stringAttribute("place_of_registration"),
        stringAttribute("main_fishing_gear")
      ),
      actionCellPredicate(
        modelGrid,
        condition(cellValue(rockGrid), greaterThan(0.0))
      ),
      actionCellPredicate(
        modelGrid,
        condition(cellValue(posidoniaGrid), greaterThan(0.0))
      ),
      actionCellPredicate(
        modelGrid,
        condition(cellValue(cymodoceaGrid), greaterThan(0.0))
      )
    );

    final var purseSeinerRegulations = forbiddenIf(
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

    final var bottomTrawlerRegulations = forbiddenIf(
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

    final var vesselField = vesselField(modelGrid);

    final var distance = haversineDistanceCalculator(modelGrid);

    final var portGrid = portGrid(
      portsFromTable(
        tableFromCsvFile(inputPath.plus("ports.csv")),
        "port_code",
        "port_name",
        "lon",
        "lat"
      ),
      bathymetricGrid,
      distance
    );

    final var pathFinder = pathFinder(bathymetricGrid, portGrid, distance);

    final var species = speciesFromData(
      tableFromCsvFile(inputPath.plus("species.csv")),
      "species_code",
      "species_name",
      "life_stage"
    );

    final var catchabilityMapsByGearCode = Table.read()
      .csv(PLACEHOLDER_CATCHABILITIES_PATH.toFile())
      .stream()
      .collect(
        groupingBy(
          row -> row.getString("gear_code"),
          LinkedHashMap::new,
          toMap(
            row ->
              multiStringKey(
                row.getString("species_code"),
                row.getString("life_stage")
              ),
            row -> row.getDouble("catchability"),
            (a, _) -> a,
            LinkedHashMap::new
          )
        )
      );

    // Scenario components of their own, so that calibration can vary them
    final var purseSeinerCatchabilities = object(
      ImmutableMap.copyOf(
        checkNotNull(catchabilityMapsByGearCode.get(PURSE_SEINE_GEAR_CODE))
      )
    );

    final var bottomTrawlerCatchabilities = object(
      ImmutableMap.copyOf(
        checkNotNull(catchabilityMapsByGearCode.get(BOTTOM_TRAWLER_GEAR_CODE))
      )
    );

    // Vessels with a gear other than these two have no gear, no engine and no
    // behaviour: they stay in port
    final var gear = choice(
      by("main_fishing_gear"),
      when(PURSE_SEINE_GEAR_CODE).then(
        indexedBiomassCatchabilityGear(
          constant(purseSeineSetDuration),
          species,
          defaultIfNull(
            mapLookup(speciesProperty("key"), purseSeinerCatchabilities),
            0.0
          ),
          massOf(1, KILOGRAM)
        )
      ),
      when(BOTTOM_TRAWLER_GEAR_CODE).then(
        indexedBiomassCatchabilityGear(
          constant(bottomTrawlerSetDuration),
          species,
          defaultIfNull(
            mapLookup(speciesProperty("key"), bottomTrawlerCatchabilities),
            0.0
          ),
          massOf(1, KILOGRAM)
        )
      ),
      otherwise(none())
    );

    final var engine = choice(
      by("main_fishing_gear"),
      when(PURSE_SEINE_GEAR_CODE).then(
        simpleEngine(
          speedOf(PURSE_SEINER_VESSEL_SPEED_IN_KNOTS, KNOT),
          volumeOf(0, LITRE) // we don't consume fuel from our infinite tank
        )
      ),
      when(BOTTOM_TRAWLER_GEAR_CODE).then(
        simpleEngine(
          speedOf(BOTTOM_TRAWLER_CRUISING_SPEED_IN_KNOTS, KNOT),
          volumeOf(0, LITRE) // we don't consume fuel from our infinite tank
        )
      ),
      otherwise(none())
    );

    final var fuelTank = vesselComponent(infiniteTank());

    // The route a vessel would travel to fish at a cell and come back: one
    // definition of the journey for both the fishing-location checks and the
    // travel cost.
    final var routeFunction = routeViaDestination(engine, pathFinder, distance);

    final var zeroBiomassAllocator = filteredAllocator(
      supplierAllocator(constant(0.0)),
      isActiveWaterCell(bathymetricGrid)
    );

    final var biomassGrids = biomassGrids(
      modelGrid,
      species,
      listOf(zeroBiomassAllocator)
    );

    final var fisheableBiomassGrids = fisheableBiomassGrids(biomassGrids);

    final var biomassSaleAccumulator = biomassSaleAccumulator();

    final var fishingActionAccumulator = fishingEventAccumulator();

    final var monthlyProcesses = scheduledRepeating(
      dateTimeAfterStarting(period(ofMonths(1))),
      period(ofMonths(1)),
      steppableSequence(
        eventClearer(biomassSaleAccumulator),
        eventClearer(fishingActionAccumulator)
      ),
      -2
    );

    final var costsKeyFromRow = multiStringKeyFromRow(
      "country_code",
      "year",
      "vessel_length",
      "gear_code"
    );

    final var hourlyCostsMap = mapFromTable(
      tableFromCsvFile(inputPath.plus("operating_costs.csv")),
      costsKeyFromRow,
      moneyFromRow("currency", "cost_per_hour_at_sea")
    );

    final var vesselLengthClass = intervalLabel(
      doubleAttribute("loa"),
      interval(0.0, 6.0, "VL0006"),
      interval(6.0, 12.0, "VL0612"),
      interval(12.0, 18.0, "VL1218"),
      interval(18.0, 24.0, "VL1824"),
      interval(24.0, 40.0, "VL2440"),
      interval(40.0, null, "VL40XX")
    );

    final var costsKeyFromVessel = multiStringKeyFromFunctions(
      stringAttribute("country_of_registration"),
      minInt(maxInt(currentYear(), constant(2013)), constant(2023)),
      vesselLengthClass,
      stringAttribute("main_fishing_gear")
    );

    final var hourlyCost = mapLookup(costsKeyFromVessel, hourlyCostsMap);

    // Markets start without prices: in SURIMI runs, prices arrive through
    // UpdateSpeciesPrices; LocalNorthwesternMedScenario schedules them from
    // prices.csv.
    final var marketGrid = marketGrid(
      portGrid,
      oneBiomassMarketPerPort(portGrid)
    );

    final var catchCategoriser = uniformCatchCategoriser(
      catchCategory(CATCH_CATEGORY)
    );

    final var hold = vesselComponent(infiniteBiomassHold(catchCategoriser));

    // One memory per vessel, keyed by gear code: what the vessel learns from
    // its fishing operations and what it decides from must be this same
    // instance.
    final KeyedMemoryFactory<String, Int2D, Bucket> vesselMemory =
      keyedMemory();

    final var optionValues = memoryBasedOptionValues(
      keyedMemorySelector(vesselMemory, stringAttribute("main_fishing_gear")),
      netValuation(
        homePortCatchValuation(catchCategoriser),
        travelCost(routeFunction, timeCost(hourlyCost))
      )
    );

    final var fleetSegmentMapper = fleetSegmentMapper(
      stringAttribute("main_fishing_gear"),
      stringAttribute("country_of_registration"),
      vesselLengthClass,
      "Industrial"
    );

    final var totalAllowableCatchQuotas = scheduledRepeatingFromStart(
      period(ofDays(1)),
      totalAllowableCatchQuotas(fleetSegmentMapper)
    );

    final var purseSeinerFishingLocationLegalityChecker =
      fishingLocationLegalityChecker(
        gear,
        engine,
        allOf(purseSeinerRegulations, totalAllowableCatchQuotas),
        pathFinder,
        distance,
        purseSeineSetDuration
      );

    final var bottomTrawlerFishingLocationLegalityChecker =
      fishingLocationLegalityChecker(
        gear,
        engine,
        allOf(bottomTrawlerRegulations, totalAllowableCatchQuotas),
        pathFinder,
        distance,
        bottomTrawlerSetDuration
      );

    // How long it would take to travel to a cell, fish there once, and travel
    // back to port. The checkers below test it before the legality checker: it
    // is cheaper than the regulations, and late in a trip it turns down most
    // cells.
    final var purseSeinerFishAndReturnDurationFunction = combinedDuration(
      routeProperty(routeFunction, "duration"),
      always(purseSeineSetDuration)
    );
    final var bottomTrawlerFishAndReturnDurationFunction = combinedDuration(
      routeProperty(routeFunction, "duration"),
      always(bottomTrawlerSetDuration)
    );

    // Purse seiners must be back in port by the first purse seiner return time
    // after they depart: at departure, the trip would depart now.
    final var purseSeinerFishingLocationAtDepartureChecker = allOf(
      condition(
        purseSeinerFishAndReturnDurationFunction,
        lessThan(durationUntil(nextOccurrenceOf(purseSeinerReturnTime)))
      ),
      purseSeinerFishingLocationLegalityChecker
    );

    // Whether a vessel at sea could travel to a cell, fish there once, and be
    // back in port by the first purse seiner return time after its trip
    // started.
    final var purseSeinerFishAndReturnInTime = condition(
      purseSeinerFishAndReturnDurationFunction,
      lessThan(
        durationUntil(
          nextOccurrenceAfter(currentTripStartDateTime(), purseSeinerReturnTime)
        )
      )
    );

    // At sea, a vessel does not fish the same cell twice in a trip. Tested
    // first, before the checks that need a path.
    final var purseSeinerFishingLocationAtSeaChecker = allOf(
      not(cellWasFishedThisTrip()),
      purseSeinerFishAndReturnInTime,
      purseSeinerFishingLocationLegalityChecker
    );

    // Bottom trawlers must be back in port within their maximum trip duration
    // of departing: at departure, that is the whole of it.
    final var bottomTrawlerFishingLocationAtDepartureChecker = allOf(
      condition(
        bottomTrawlerFishAndReturnDurationFunction,
        lessThan(constant(bottomTrawlerMaximumTripDuration))
      ),
      bottomTrawlerFishingLocationLegalityChecker
    );

    // Whether a vessel at sea could travel to a cell, fish there once, and be
    // back in port within its maximum trip duration of its trip's start.
    final var bottomTrawlerFishAndReturnInTime = condition(
      bottomTrawlerFishAndReturnDurationFunction,
      lessThan(
        durationUntil(
          dateTimeAfter(
            currentTripStartDateTime(),
            bottomTrawlerMaximumTripDuration
          )
        )
      )
    );

    final var bottomTrawlerFishingLocationAtSeaChecker = allOf(
      not(cellWasFishedThisTrip()),
      bottomTrawlerFishAndReturnInTime,
      bottomTrawlerFishingLocationLegalityChecker
    );

    final var purseSeinerReadyForDeparture = checkThat(
      allOf(
        isPermitted(departNow(gear), totalAllowableCatchQuotas),
        condition(currentTime(), afterTime(constant(time(21, 59, 59)))),
        condition(
          currentDayOfWeek(),
          in(constant(setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY)))
        ),
        not(vesselHasStartedTripToday())
      )
    );

    final var purseSeinerWaitUntilNextCheckpoint = waitFor(
      durationUntil(nextOccurrenceOf(time(6, 0, 0), time(22, 0, 0)))
    );

    // Bottom trawlers depart in the morning and return the same day (per Miquel
    // Ortega: ~07:00 departure, ~16:30-18:00 return), unlike purse seiners'
    // overnight schedule above - this used to share the purse seiner's
    // 06:00/22:00 checkpoint pair, which made trawlers only eligible to depart
    // after 22:00.
    final var bottomTrawlerReadyForDeparture = checkThat(
      allOf(
        isPermitted(departNow(gear), totalAllowableCatchQuotas),
        condition(currentTime(), afterTime(constant(time(6, 59, 59)))),
        condition(
          currentDayOfWeek(),
          in(constant(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)))
        ),
        not(vesselHasStartedTripToday())
      )
    );

    final var bottomTrawlerWaitUntilNextCheckpoint = waitFor(
      durationUntil(nextOccurrenceOf(time(7, 0, 0)))
    );

    final var purseSeinerFirstDestination = epsilonGreedyDestination(
      PURSE_SEINER_EXPLORATION_PROBABILITY,
      neighbourhoodGridExplorer(
        pathFinder,
        purseSeinerFishingLocationAtDepartureChecker,
        shiftedInt(randomPoisson(PURSE_SEINER_MEAN_EXPLORATION_RADIUS), 1),
        MAXIMUM_EXPLORATION_WIDENING,
        bestKnownOption(optionValues, currentCell())
      ),
      greedyPicker(optionValues, purseSeinerFishingLocationAtDepartureChecker)
    );

    final var chooseNextPurseSeinerDestination = chooseNextDestination(
      epsilonGreedyDestination(
        PURSE_SEINER_EXPLORATION_PROBABILITY,
        neighbourhoodGridExplorer(
          pathFinder,
          purseSeinerFishingLocationAtSeaChecker,
          shiftedInt(randomPoisson(PURSE_SEINER_MEAN_EXPLORATION_RADIUS), 1),
          MAXIMUM_EXPLORATION_WIDENING,
          currentCell()
        ),
        greedyPicker(optionValues, purseSeinerFishingLocationAtSeaChecker)
      )
    );

    final var bottomTrawlerFirstDestination = epsilonGreedyDestination(
      BOTTOM_TRAWLER_EXPLORATION_PROBABILITY,
      neighbourhoodGridExplorer(
        pathFinder,
        bottomTrawlerFishingLocationAtDepartureChecker,
        shiftedInt(randomPoisson(BOTTOM_TRAWLER_MEAN_EXPLORATION_RADIUS), 1),
        MAXIMUM_EXPLORATION_WIDENING,
        bestKnownOption(optionValues, currentCell())
      ),
      greedyPicker(optionValues, bottomTrawlerFishingLocationAtDepartureChecker)
    );

    final var chooseNextBottomTrawlerDestination = chooseNextDestination(
      epsilonGreedyDestination(
        BOTTOM_TRAWLER_EXPLORATION_PROBABILITY,
        neighbourhoodGridExplorer(
          pathFinder,
          bottomTrawlerFishingLocationAtSeaChecker,
          shiftedInt(randomPoisson(BOTTOM_TRAWLER_MEAN_EXPLORATION_RADIUS), 1),
          MAXIMUM_EXPLORATION_WIDENING,
          currentCell()
        ),
        greedyPicker(optionValues, bottomTrawlerFishingLocationAtSeaChecker)
      )
    );

    final var discardRatiosTable = tableFromCsvFile(
      inputPath.plus("discard_ratios.csv")
    );
    final var discardRatiosKeyFromRow = multiStringKeyFromRow(
      "species_code",
      "life_stage",
      "gear_code"
    );

    final var purseSeineDiscardRates = discardRates(
      species,
      defaultIfNull(
        tableLookup(
          multiStringKeyFromFunctions(
            speciesProperty("code"),
            speciesProperty("lifeStage"),
            always(PURSE_SEINE_GEAR_CODE)
          ),
          discardRatiosTable,
          discardRatiosKeyFromRow,
          doubleFromRow("discard_ratio")
        ),
        0.0
      )
    );
    final var bottomTrawlerDiscardRates = discardRates(
      species,
      defaultIfNull(
        tableLookup(
          multiStringKeyFromFunctions(
            speciesProperty("code"),
            speciesProperty("lifeStage"),
            always(BOTTOM_TRAWLER_GEAR_CODE)
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
      gear,
      hold,
      fuelTank,
      currentCellFisheable(fisheableBiomassGrids),
      compositeDispositionProcess(
        purseSeineDiscardRates,
        proportionallyLimitingBiomassToHold(),
        discardMortality
      )
    );
    final var bottomTrawlerFishingTask = fishing(
      gear,
      hold,
      fuelTank,
      currentCellFisheable(fisheableBiomassGrids),
      compositeDispositionProcess(
        bottomTrawlerDiscardRates,
        proportionallyLimitingBiomassToHold(),
        discardMortality
      )
    );

    // Each run of the behaviour either travels home, when the vessel is away
    // from its home port, or, at the home port, makes a trip or waits. A
    // change to the vessel takes effect between runs, so a vessel given a new
    // home port travels there and then departs from it.
    final var travelHome = travelAlongPathTo(
      engine,
      fuelTank,
      pathFinder,
      distance,
      homePortCell(),
      vesselEventManager()
    );

    final var travelToTripDestination = travelAlongPathTo(
      engine,
      fuelTank,
      pathFinder,
      distance,
      currentTripDestinationCell(),
      currentTripEventManager()
    );

    final var purseSeinerBehaviour = selectorTask(
      sequenceTask(
        checkThat(vesselIsAt(homePortCell())),
        succeedOrWait(
          sequenceTask(
            purseSeinerReadyForDeparture,
            trip(
              purseSeinerFirstDestination,
              untilFail(
                sequenceTask(
                  travelToTripDestination,
                  purseSeinerFishingTask,
                  // No time to fish once more here and travel home: going via
                  // any other cell takes longer, so the trip ends without a
                  // search.
                  checkThat(currentCell(), purseSeinerFishAndReturnInTime),
                  chooseNextPurseSeinerDestination
                )
              ),
              setDestinationToOrigin(),
              travelToTripDestination,
              arriveInPort(),
              payTripCost(tripCostFromHourlyCosts(hourlyCost)),
              landCatches(hold, constant(hours(1)))
            )
          ),
          purseSeinerWaitUntilNextCheckpoint
        )
      ),
      travelHome
    );

    final var bottomTrawlerBehaviour = selectorTask(
      sequenceTask(
        checkThat(vesselIsAt(homePortCell())),
        succeedOrWait(
          sequenceTask(
            bottomTrawlerReadyForDeparture,
            trip(
              bottomTrawlerFirstDestination,
              untilFail(
                sequenceTask(
                  travelToTripDestination,
                  bottomTrawlerFishingTask,
                  // No time to fish once more here and travel home: going via
                  // any other cell takes longer, so the trip ends without a
                  // search.
                  checkThat(currentCell(), bottomTrawlerFishAndReturnInTime),
                  chooseNextBottomTrawlerDestination
                )
              ),
              setDestinationToOrigin(),
              travelToTripDestination,
              arriveInPort(),
              payTripCost(tripCostFromHourlyCosts(hourlyCost)),
              landCatches(hold, constant(hours(1)))
            )
          ),
          bottomTrawlerWaitUntilNextCheckpoint
        )
      ),
      travelHome
    );

    final var behaviour = choice(
      by("main_fishing_gear"),
      when(PURSE_SEINE_GEAR_CODE).then(purseSeinerBehaviour),
      when(BOTTOM_TRAWLER_GEAR_CODE).then(bottomTrawlerBehaviour),
      otherwise(none())
    );

    final var fleet = fleet(vesselField, portGrid, marketGrid);
    final var socialNetwork = socialNetwork(fleet);
    final var cliqueDynamics = cliqueDynamics(
      socialNetwork,
      multiStringKeyFromFunctions(
        stringAttribute("place_of_registration"),
        stringAttribute("main_fishing_gear")
      ),
      MAXIMUM_CLIQUE_SIZE
    );
    final var fishingEventSharer = fishingEventSharer(socialNetwork);

    // What a vessel learns from its own fishing and from what its friends
    // share, into its memory for the gear it fished with
    final var ownCatchRecorder = vesselComponent(
      vesselEventListener(
        fishingEventRecorder(
          modelGrid,
          keyedMemorySelector(
            vesselMemory,
            stringAttribute("main_fishing_gear")
          ),
          exponentialMovingAverageOfBuckets(OWN_LEARNING_ALPHA)
        )
      )
    );
    final var sharedCatchRecorder = vesselComponent(
      sharedFishingEventListener(
        fishingEventRecorder(
          modelGrid,
          keyedMemorySelector(
            vesselMemory,
            stringAttribute("main_fishing_gear")
          ),
          exponentialMovingAverageOfBuckets(SOCIAL_LEARNING_ALPHA)
        ),
        fishingEventSharer
      )
    );

    final var vesselSpecification = vesselSpecification(fleet, behaviour)
      .homePortAttribute("place_of_registration")
      .nameAttribute("name_of_vessel")
      .component("hold", hold)
      .component("gear", gear)
      .component("engine", engine)
      .component("fuelTank", fuelTank)
      .component("ownCatchRecorder", ownCatchRecorder)
      .component("sharedCatchRecorder", sharedCatchRecorder)
      .build();

    final var vesselRegister = scheduledByDateTime(
      vesselRegister(
        tableFromCsvFile(inputPath.plus("fleet_register.csv")),
        vesselSpecification
      )
    );

    final var fixedCostCollector = scheduledRepeatingFromStart(
      period(ofDays(1)),
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

    final var directoryRemover = finalProcess(
      directoryRemover(outputPath, false)
    );

    builder
      .startingDateTime(startOf(START_DATE))
      .component("purseSeinerCatchabilities", purseSeinerCatchabilities)
      .component("bottomTrawlerCatchabilities", bottomTrawlerCatchabilities)
      .component("species", species)
      .component("bathymetricGrid", bathymetricGrid)
      .component("biomassGrids", biomassGrids)
      .component("fisheableBiomassGrids", fisheableBiomassGrids)
      .component("marketGrid", marketGrid)
      .component("portGrid", portGrid)
      .component("purseSeinerRegulations", purseSeinerRegulations)
      .component("bottomTrawlerRegulations", bottomTrawlerRegulations)
      .component("totalAllowableCatchQuotas", totalAllowableCatchQuotas)
      .component("fleetSegmentMapper", fleetSegmentMapper)
      .component("vesselField", vesselField)
      .component("modelGrid", modelGrid)
      .component("monthlyProcesses", monthlyProcesses)
      .component("fleet", fleet)
      .component("vesselSpecification", vesselSpecification)
      .component("vesselRegister", vesselRegister)
      .component("socialNetwork", socialNetwork)
      .component("cliqueDynamics", cliqueDynamics)
      .component("fishingEventSharer", fishingEventSharer)
      .component("fixedCostCollector", fixedCostCollector)
      .component("fishingActionAccumulator", fishingActionAccumulator)
      .component("biomassSaleAccumulator", biomassSaleAccumulator)
      .component("directoryRemover", directoryRemover);

    return builder.build();
  }
}
