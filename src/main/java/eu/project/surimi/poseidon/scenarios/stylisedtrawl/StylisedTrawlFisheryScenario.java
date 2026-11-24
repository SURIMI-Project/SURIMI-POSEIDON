/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2026, University of Oxford.
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

package eu.project.surimi.poseidon.scenarios.stylisedtrawl;

import static eu.project.surimi.poseidon.vessels.Factories.*;
import static java.time.DayOfWeek.*;
import static java.time.Period.ofDays;
import static java.util.Map.entry;
import static si.uom.NonSI.KNOT;
import static tech.units.indriya.unit.Units.KILOGRAM;
import static tech.units.indriya.unit.Units.LITRE;
import static uk.ac.ox.poseidon.biology.allocators.Factories.proportionOfCarryingCapacityAllocator;
import static uk.ac.ox.poseidon.biology.biomass.Factories.*;
import static uk.ac.ox.poseidon.biology.species.Factories.species;
import static uk.ac.ox.poseidon.biology.species.extractors.Factories.speciesProperty;
import static uk.ac.ox.poseidon.core.aggregators.Factories.maxAggregator;
import static uk.ac.ox.poseidon.core.functions.Factories.*;
import static uk.ac.ox.poseidon.core.predicates.Factories.condition;
import static uk.ac.ox.poseidon.core.predicates.Factories.in;
import static uk.ac.ox.poseidon.core.predicates.comparable.Factories.lessThan;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.allOf;
import static uk.ac.ox.poseidon.core.predicates.logical.Factories.not;
import static uk.ac.ox.poseidon.core.predicates.temporal.Factories.timeIsBefore;
import static uk.ac.ox.poseidon.core.providers.Factories.shiftedInt;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.always;
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
import static uk.ac.ox.poseidon.fishing.market.Factories.initialPrices;
import static uk.ac.ox.poseidon.fishing.market.Factories.marketGrid;
import static uk.ac.ox.poseidon.fishing.market.Factories.oneBiomassMarketPerPort;
import static uk.ac.ox.poseidon.fishing.market.Factories.price;
import static uk.ac.ox.poseidon.fishing.market.Factories.priceEntry;
import static uk.ac.ox.poseidon.fishing.market.Factories.uniformMarketPrices;
import static uk.ac.ox.poseidon.fishing.money.Factories.moneyFromRow;
import static uk.ac.ox.poseidon.fishing.regulations.predicates.Factories.fishingCellLegalityChecker;
import static uk.ac.ox.poseidon.fishing.social.Factories.*;
import static uk.ac.ox.poseidon.fishing.tasks.branches.Factories.fallbackTask;
import static uk.ac.ox.poseidon.fishing.tasks.branches.Factories.sequenceTask;
import static uk.ac.ox.poseidon.fishing.tasks.decorators.Factories.untilFail;
import static uk.ac.ox.poseidon.fishing.tasks.destinations.Factories.chooseNextDestination;
import static uk.ac.ox.poseidon.fishing.tasks.fishing.Factories.fishing;
import static uk.ac.ox.poseidon.fishing.tasks.general.Factories.*;
import static uk.ac.ox.poseidon.fishing.tasks.landings.Factories.landCatches;
import static uk.ac.ox.poseidon.fishing.tasks.travel.Factories.*;
import static uk.ac.ox.poseidon.fishing.travel.Factories.timeCost;
import static uk.ac.ox.poseidon.fishing.travel.Factories.travelTimeHomeFromCell;
import static uk.ac.ox.poseidon.fishing.travel.Factories.travelTimeToCell;
import static uk.ac.ox.poseidon.fishing.trips.Factories.cellWasFishedThisTrip;
import static uk.ac.ox.poseidon.fishing.vessels.Factories.*;
import static uk.ac.ox.poseidon.fishing.vessels.engines.Factories.simpleEngine;
import static uk.ac.ox.poseidon.fishing.vessels.gears.Factories.indexedBiomassCatchabilityGear;
import static uk.ac.ox.poseidon.fishing.vessels.holds.Factories.infiniteBiomassHold;
import static uk.ac.ox.poseidon.fishing.vessels.tanks.Factories.infiniteTank;
import static uk.ac.ox.poseidon.fishing.vessels.values.Factories.*;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.doubleAttribute;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.stringAttribute;
import static uk.ac.ox.poseidon.geography.Factories.envelope;
import static uk.ac.ox.poseidon.geography.allocators.Factories.filteredAllocator;
import static uk.ac.ox.poseidon.geography.allocators.Factories.supplierAllocator;
import static uk.ac.ox.poseidon.geography.bathymetry.Factories.bathymetricGridFromGridFile;
import static uk.ac.ox.poseidon.geography.distance.Factories.haversineDistanceCalculator;
import static uk.ac.ox.poseidon.geography.grids.Factories.*;
import static uk.ac.ox.poseidon.geography.paths.Factories.pathFinder;
import static uk.ac.ox.poseidon.geography.paths.Factories.pathLengthCalculator;
import static uk.ac.ox.poseidon.geography.ports.Factories.portGrid;
import static uk.ac.ox.poseidon.geography.ports.Factories.portsFromTable;
import static uk.ac.ox.poseidon.geography.predicates.Factories.inRectangularArea;
import static uk.ac.ox.poseidon.geography.predicates.Factories.isActiveWaterCell;
import static uk.ac.ox.poseidon.io.paths.Factories.path;
import static uk.ac.ox.poseidon.io.tables.Factories.*;
import static uk.ac.ox.poseidon.regulations.Factories.forbiddenIf;
import static uk.ac.ox.poseidon.regulations.predicates.spatial.Factories.actionCoordinatePredicate;
import static uk.ac.ox.poseidon.regulations.predicates.temporal.Factories.betweenDates;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import uk.ac.ox.poseidon.core.Scenario;

/**
 * A stylised trawl fishery on the Northwestern Mediterranean map, for the
 * project video: an illustration of how introducing a marine protected area
 * changes a fishery, not a prediction for the real Northwestern Mediterranean.
 * <p>
 * The map and the ports are the real ones, read from
 * {@code inputs/northwestern_med}, except that the French EEZ is left out of
 * the grid ({@code scenarios/stylised_trawl_exclusion_grid.asc}), so that no
 * fish go unfished there. The fleet is the real Spanish bottom trawlers too: the
 * vessels that were bottom trawlers throughout their register entries, in
 * {@code scenarios/stylised_trawl_fleet_register.csv}.
 * <p>
 * Everything else is simplified: one invented species, demersal fish, that
 * starts at half of an invented carrying capacity and grows logistically across
 * every cell, a fixed price, and no real regulations: the only one is the demonstration MPA, a rectangle
 * closed to every trawler from a set date.
 * <p>
 * It is a standalone scenario, not a variant of {@link
 * eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario},
 * and it is run locally only: the server does not serve it.
 */
public class StylisedTrawlFisheryScenario implements Supplier<Scenario> {

  public static final LocalDate START_DATE = LocalDate.of(2013, 1, 1);
  static final Path INPUT_PATH = Path.of("inputs", "northwestern_med");
  // The Northwestern Mediterranean exclusion grid without the French EEZ,
  // which is left out of the model, not closed to fishing
  private static final Path EXCLUSION_GRID_PATH = Path.of(
    "scenarios",
    "stylised_trawl_exclusion_grid.asc"
  );
  private static final Path FLEET_REGISTER_PATH = Path.of(
    "scenarios",
    "stylised_trawl_fleet_register.csv"
  );

  static final String DEMERSAL_FISH_CODE = "DEM";
  private static final String CATCH_CATEGORY = "Fresh - Whole";
  private static final String CURRENCY_CODE = "EUR";
  private static final double DEMERSAL_FISH_PRICE_PER_KG = 7.0;

  // The carrying capacity of every water cell is a placeholder until the demo
  // is tuned.
  private static final double CELL_CARRYING_CAPACITY_IN_KG = 20_000.0;
  private static final double STARTING_BIOMASS_PROPORTION = 0.5;
  private static final double DAILY_GROWTH_RATE = 0.02;
  private static final double DIFFERENTIAL_PERCENTAGE_TO_MOVE = 0.05;
  private static final double PERCENTAGE_LIMIT_ON_DAILY_MOVEMENT = 0.05;
  private static final double CATCHABILITY = 0.0125;

  // The demonstration MPA: a rectangle, on cell boundaries, closed to every
  // trawler from MPA_START_DATE on. It spans the whole width of the fished
  // coastal strip from Alcanar to Burriana, so that fish build up inside
  // it and spill over only at its two ends along the coast, while every home
  // port keeps open fishing grounds within a day trip.
  private static final LocalDate MPA_START_DATE = LocalDate.of(2013, 7, 1);
  private static final LocalDate FAR_FUTURE = LocalDate.of(2999, 12, 31);
  private static final double MPA_MINIMUM_LONGITUDE = -0.1022;
  private static final double MPA_MAXIMUM_LONGITUDE = 1.4805;
  private static final double MPA_MINIMUM_LATITUDE = 39.7628;
  private static final double MPA_MAXIMUM_LATITUDE = 40.5958;

  // Copied from the Northwestern Mediterranean scenario as a starting point;
  // this scenario's own from here on
  private static final double FIRST_HAND_LEARNING_ALPHA = 0.5;
  private static final double SECOND_HAND_LEARNING_ALPHA = 0.2;
  private static final int MAXIMUM_CLIQUE_SIZE = 5;
  private static final double EXPLORATION_PROBABILITY = 0.2;
  private static final int MEAN_EXPLORATION_RADIUS = 1;
  private static final int MAXIMUM_EXPLORATION_WIDENING = 3;
  private static final double CRUISING_SPEED_IN_KNOTS = 9;

  /**
   * @return a freshly built {@link Scenario}.
   */
  @Override
  public Scenario get() {
    final Scenario.ScenarioBuilder builder = Scenario.builder();

    final var setDuration = hours(3);
    final var maximumTripDuration = hours(12);
    // The earliest time of day trawlers may depart: their departure check and
    // their wait between checks both read it, so they cannot drift apart.
    final var departureTime = time(7, 0, 0);

    final var inputPath = path(INPUT_PATH);
    final var bathymetricGridPath = inputPath.plus("bathymetry_grid.asc");

    final var allCellsGrid = modelGridFromGridFile(bathymetricGridPath);
    final var modelGrid = modelGridWithActiveCells(
      allCellsGrid,
      cellSetFromGridFile(path(EXCLUSION_GRID_PATH), allCellsGrid, 0)
    );

    final var bathymetricGrid = bathymetricGridFromGridFile(
      bathymetricGridPath,
      modelGrid,
      maxAggregator(),
      false
    );

    // Over the rectangle's coordinates, unlike the others, which test cells
    final var demonstrationMpa = allOf(
      betweenDates(date(MPA_START_DATE), date(FAR_FUTURE)),
      actionCoordinatePredicate(
        inRectangularArea(
          envelope(
            MPA_MINIMUM_LONGITUDE,
            MPA_MAXIMUM_LONGITUDE,
            MPA_MINIMUM_LATITUDE,
            MPA_MAXIMUM_LATITUDE
          )
        )
      )
    );

    // The demonstration MPA is the only regulation
    final var bottomTrawlerRegulations = forbiddenIf(demonstrationMpa);

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

    final var pathLengthCalculator = pathLengthCalculator(pathFinder, distance);

    final var demersalFish = species(DEMERSAL_FISH_CODE, "Demersal fish", null);
    final var speciesList = listOf(demersalFish);

    // A scenario component of its own, so that calibration can vary it
    final var catchabilities = object(
      ImmutableMap.of(multiStringKey(DEMERSAL_FISH_CODE, null), CATCHABILITY)
    );

    final var gear = vesselComponent(
      indexedBiomassCatchabilityGear(
        setDuration,
        speciesList,
        mapLookup(speciesProperty("key"), catchabilities, 0.0),
        massOf(1, KILOGRAM)
      )
    );

    final var engine = vesselComponent(
      simpleEngine(
        speedOf(CRUISING_SPEED_IN_KNOTS, KNOT),
        volumeOf(0, LITRE) // we don't consume fuel from our infinite tank
      )
    );

    final var fuelTank = vesselComponent(infiniteTank());

    final var travelTimeToCell = travelTimeToCell(engine, pathLengthCalculator);
    final var travelTimeHomeFromCell = travelTimeHomeFromCell(
      engine,
      pathLengthCalculator
    );

    final var carryingCapacityGrid = carryingCapacityGrid(
      doubleGridFromAllocator(
        modelGrid,
        filteredAllocator(
          supplierAllocator(CELL_CARRYING_CAPACITY_IN_KG),
          isActiveWaterCell(bathymetricGrid)
        )
      )
    );

    final var biomassGrid = biomassGrid(
      modelGrid,
      demersalFish,
      proportionOfCarryingCapacityAllocator(
        carryingCapacityGrid,
        STARTING_BIOMASS_PROPORTION
      )
    );
    final var biomassGrids = listOf(biomassGrid);

    final var fisheableBiomassGrids = fisheableBiomassGrids(biomassGrids);

    // Growth, then diffusion, every day
    final var demersalFishGrower = scheduledRepeating(
      dateTimeAfterStarting(days(1)),
      period(ofDays(1)),
      independentBiomassGrower(
        biomassGrid,
        carryingCapacityGrid,
        logisticGrowthRule(DAILY_GROWTH_RATE)
      ),
      0
    );
    final var demersalFishDiffuser = scheduledRepeating(
      dateTimeAfterStarting(days(1)),
      period(ofDays(1)),
      biomassDiffuser(
        biomassGrid,
        carryingCapacityGrid,
        smoothBiomassDiffusionRule(
          DIFFERENTIAL_PERCENTAGE_TO_MOVE,
          PERCENTAGE_LIMIT_ON_DAILY_MOVEMENT
        )
      ),
      1
    );

    final var biomassSaleAccumulator = biomassSaleAccumulator();

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
      entry("VL0006", 0.0),
      entry("VL0612", 6.0),
      entry("VL1218", 12.0),
      entry("VL1824", 18.0),
      entry("VL2440", 24.0),
      entry("VL40XX", 40.0)
    );

    final var costsKeyFromVessel = multiStringKeyFromFunctions(
      stringAttribute("country_of_registration"),
      minInt(maxInt(currentYear(), 2013), 2023),
      vesselLengthClass,
      stringAttribute("main_fishing_gear")
    );

    final var hourlyCost = mapLookup(costsKeyFromVessel, hourlyCostsMap);

    final var marketGrid = marketGrid(
      portGrid,
      oneBiomassMarketPerPort(portGrid, CURRENCY_CODE)
    );

    final var catchCategory = catchCategory(CATCH_CATEGORY);
    final var catchCategoriser = uniformCatchCategoriser(catchCategory);

    // Markets start without prices and this scenario has no price driver, so
    // the prices are fixed
    final var initialPrices = initialPrices(
      uniformMarketPrices(
        marketGrid,
        listOf(
          priceEntry(
            catchCategory,
            demersalFish,
            price(DEMERSAL_FISH_PRICE_PER_KG, CURRENCY_CODE, "kg")
          )
        )
      )
    );

    final var hold = vesselComponent(infiniteBiomassHold(catchCategoriser));

    // One memory per vessel: what the vessel learns from its fishing operations
    // and what it decides from must be this same instance.
    final var catchMemory = catchesByCellPartitionedBy(
      stringAttribute("main_fishing_gear")
    );

    final var cellValues = netOptionValues(
      memoryBasedOptionValues(
        catchMemory,
        homePortCatchValuation(catchCategoriser, marketGrid)
      ),
      timeCost(
        combinedDuration(travelTimeToCell, travelTimeHomeFromCell),
        hourlyCost
      )
    );

    final var currentDateTime = currentDateTime();

    // Fishing starts on arrival: no fishing hours here, so no wait.
    final var fishingCellLegalityChecker = fishingCellLegalityChecker(
      gear,
      bottomTrawlerRegulations,
      dateTimeAfter(currentDateTime, travelTimeToCell)
    );

    // How long it would take to travel to a cell, fish there once, and travel
    // back to port.
    final var fishAndReturnDurationFunction = combinedDuration(
      travelTimeToCell,
      always(setDuration),
      travelTimeHomeFromCell
    );

    // Trawlers must be back in port within their maximum trip duration of
    // departing: at departure, that is the whole of it.
    final var cellEligibilityCriteriaAtDeparture = allOf(
      isActiveWaterCell(bathymetricGrid),
      condition(fishAndReturnDurationFunction, lessThan(maximumTripDuration)),
      fishingCellLegalityChecker
    );

    final var fishAndReturnInTime = condition(
      fishAndReturnDurationFunction,
      lessThan(
        durationBetween(
          currentDateTime,
          dateTimeAfter(currentTripStartDateTime(), maximumTripDuration)
        )
      )
    );

    // At sea, a vessel does not fish the same cell twice in a trip
    final var cellEligibilityCriteriaAtSea = allOf(
      isActiveWaterCell(bathymetricGrid),
      not(cellWasFishedThisTrip()),
      fishAndReturnInTime,
      fishingCellLegalityChecker
    );

    final var hasStartedFishingTripToday = hasStartedFishingTripToday();

    // Trawlers depart in the morning and return the same day
    final var readyForDeparture = checkThat(
      allOf(
        condition(currentTime(), not(timeIsBefore(departureTime))),
        condition(
          currentDayOfWeek(),
          in(listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY))
        ),
        not(hasStartedFishingTripToday)
      )
    );

    final var waitUntilNextCheckpoint = waitFor(
      durationBetween(
        currentDateTime,
        nextOccurrenceAfter(currentDateTime, departureTime)
      )
    );

    final var firstDestination = epsilonGreedyPicker(
      EXPLORATION_PROBABILITY,
      nearbyCellPicker(
        pathFinder,
        cellEligibilityCriteriaAtDeparture,
        shiftedInt(randomPoisson(MEAN_EXPLORATION_RADIUS), 1),
        MAXIMUM_EXPLORATION_WIDENING,
        fallbackPicker(greedyPicker(cellValues), some(currentCell()))
      ),
      greedyPicker(cellValues, cellEligibilityCriteriaAtDeparture)
    );

    final var chooseNextDestination = chooseNextDestination(
      epsilonGreedyPicker(
        EXPLORATION_PROBABILITY,
        nearbyCellPicker(
          pathFinder,
          cellEligibilityCriteriaAtSea,
          shiftedInt(randomPoisson(MEAN_EXPLORATION_RADIUS), 1),
          MAXIMUM_EXPLORATION_WIDENING,
          some(currentCell())
        ),
        greedyPicker(cellValues, cellEligibilityCriteriaAtSea)
      )
    );

    final var fishingTask = fishing(
      gear,
      hold,
      fuelTank,
      currentCellFisheable(fisheableBiomassGrids),
      compositeDispositionProcess(proportionallyLimitingBiomassToHold())
    );

    final var travelToTripDestination = travelAlongPathTo(
      engine,
      fuelTank,
      pathFinder,
      distance,
      currentTripDestination(),
      currentTripEventManager()
    );

    final var travelHome = sequenceTask(
      checkThat(not(vesselIsDockedAt(homePort()))),
      trip(some(homePort()), travelToTripDestination, dockAtDestination())
    );

    final var bottomTrawlerBehaviour = fallbackTask(
      sequenceTask(
        checkThat(vesselIsDockedAt(homePort())),
        succeedOrWait(
          sequenceTask(
            readyForDeparture,
            fishingTrip(
              firstDestination,
              timeAtSeaCost(hourlyCost),
              untilFail(
                sequenceTask(
                  travelToTripDestination,
                  fishingTask,
                  // No time to fish once more here and travel home: going via
                  // any other cell takes longer, so the trip ends without a
                  // search.
                  checkThat(currentCell(), fishAndReturnInTime),
                  chooseNextDestination
                )
              ),
              setDestinationToOrigin(),
              travelToTripDestination,
              dockAtDestination(),
              landCatches(hold, hours(1), marketGrid)
            )
          ),
          waitUntilNextCheckpoint
        )
      ),
      travelHome
    );

    final var fleet = fleet(vesselField, portGrid);
    final var socialNetwork = socialNetwork(fleet);
    final var cliqueDynamics = cliqueDynamics(
      socialNetwork,
      List.of(homePort(), stringAttribute("main_fishing_gear")),
      MAXIMUM_CLIQUE_SIZE
    );
    final var fishingActionSharer = fishingActionSharer(socialNetwork);

    final var ownCatchRecorder = vesselComponent(
      vesselEventListener(
        fishingActionRecorder(
          fishingCell(),
          retainedCatch(),
          catchMemory,
          exponentialMovingAverageOfBuckets(FIRST_HAND_LEARNING_ALPHA)
        )
      )
    );
    final var sharedCatchRecorder = vesselComponent(
      sharedFishingActionListener(
        fishingActionRecorder(
          fishingCell(),
          retainedCatch(),
          catchMemory,
          exponentialMovingAverageOfBuckets(SECOND_HAND_LEARNING_ALPHA)
        ),
        fishingActionSharer
      )
    );

    final var vesselSpecification = vesselSpecification(
      fleet,
      vesselComponent(bottomTrawlerBehaviour),
      entry("hold", hold),
      entry("gear", gear),
      entry("engine", engine),
      entry("fuelTank", fuelTank),
      entry("ownCatchRecorder", ownCatchRecorder),
      entry("sharedCatchRecorder", sharedCatchRecorder)
    );

    final var vesselRegister = scheduledByDateTime(
      euFleetRegister(
        tableFromCsvFile(path(FLEET_REGISTER_PATH)),
        vesselSpecification,

        List.of("main_fishing_gear", "country_of_registration", "loa")
      )
    );

    builder
      .startingDateTime(startOf(START_DATE))
      .component("catchabilities", catchabilities)
      .component("species", speciesList)
      .component("bathymetricGrid", bathymetricGrid)
      .component("biomassGrids", biomassGrids)
      .component("fisheableBiomassGrids", fisheableBiomassGrids)
      .component("demersalFishGrower", demersalFishGrower)
      .component("demersalFishDiffuser", demersalFishDiffuser)
      .component("marketGrid", marketGrid)
      .component("initialPrices", initialPrices)
      .component("portGrid", portGrid)
      .component("bottomTrawlerRegulations", bottomTrawlerRegulations)
      .component("vesselField", vesselField)
      .component("modelGrid", modelGrid)
      .component("fleet", fleet)
      .component("vesselSpecification", vesselSpecification)
      .component("vesselRegister", vesselRegister)
      .component("socialNetwork", socialNetwork)
      .component("cliqueDynamics", cliqueDynamics)
      .component("fishingActionSharer", fishingActionSharer)
      .component("biomassSaleAccumulator", biomassSaleAccumulator);

    return builder.build();
  }
}
