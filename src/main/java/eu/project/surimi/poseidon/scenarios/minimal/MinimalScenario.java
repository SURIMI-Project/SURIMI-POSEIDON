/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025-2026, University of Oxford.
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

package eu.project.surimi.poseidon.scenarios.minimal;

import static eu.project.surimi.poseidon.server.fleet.Factories.fleetSegmentMapper;
import static java.util.stream.IntStream.range;
import static si.uom.NonSI.KNOT;
import static si.uom.NonSI.TONNE;
import static tech.units.indriya.quantity.Quantities.getQuantity;
import static tech.units.indriya.unit.Units.KILOGRAM;
import static tech.units.indriya.unit.Units.LITRE;
import static uk.ac.ox.poseidon.biology.allocators.Factories.fullCarryingCapacityAllocator;
import static uk.ac.ox.poseidon.biology.biomass.Factories.*;
import static uk.ac.ox.poseidon.biology.species.Factories.species;
import static uk.ac.ox.poseidon.biology.species.Factories.speciesByCode;
import static uk.ac.ox.poseidon.core.functions.Factories.intervalLabel;
import static uk.ac.ox.poseidon.core.functions.IntervalLabelFactory.interval;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.always;
import static uk.ac.ox.poseidon.core.providers.constant.Factories.constant;
import static uk.ac.ox.poseidon.core.quantities.Factories.*;
import static uk.ac.ox.poseidon.core.quantities.VolumetricFlowRateFactory.LITRE_PER_HOUR;
import static uk.ac.ox.poseidon.core.schedule.Factories.scheduledByDateTime;
import static uk.ac.ox.poseidon.core.time.Factories.hours;
import static uk.ac.ox.poseidon.core.time.Factories.startOf;
import static uk.ac.ox.poseidon.core.utils.Factories.*;
import static uk.ac.ox.poseidon.fishing.catches.Factories.catchCategory;
import static uk.ac.ox.poseidon.fishing.catches.Factories.uniformCatchCategoriser;
import static uk.ac.ox.poseidon.fishing.catches.disposition.Factories.*;
import static uk.ac.ox.poseidon.fishing.choices.Factories.destinationAt;
import static uk.ac.ox.poseidon.fishing.fields.Factories.vesselField;
import static uk.ac.ox.poseidon.fishing.fisheables.Factories.currentCellFisheable;
import static uk.ac.ox.poseidon.fishing.market.Factories.*;
import static uk.ac.ox.poseidon.fishing.tasks.fishing.Factories.fishing;
import static uk.ac.ox.poseidon.fishing.tasks.fishing.Factories.fishingEventAccumulator;
import static uk.ac.ox.poseidon.fishing.tasks.landings.Factories.landCatches;
import static uk.ac.ox.poseidon.fishing.tasks.travel.Factories.dockAtDestination;
import static uk.ac.ox.poseidon.fishing.tasks.travel.Factories.setDestinationToOrigin;
import static uk.ac.ox.poseidon.fishing.tasks.travel.Factories.travelAlongPathTo;
import static uk.ac.ox.poseidon.fishing.vessels.Factories.*;
import static uk.ac.ox.poseidon.fishing.vessels.engines.Factories.simpleEngine;
import static uk.ac.ox.poseidon.fishing.vessels.gears.Factories.fixedBiomassProportionGear;
import static uk.ac.ox.poseidon.fishing.vessels.holds.Factories.infiniteBiomassHold;
import static uk.ac.ox.poseidon.fishing.vessels.tanks.Factories.fullTank;
import static uk.ac.ox.poseidon.fishing.vessels.values.Factories.currentTripDestination;
import static uk.ac.ox.poseidon.fishing.vessels.values.Factories.currentTripEventManager;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.doubleAttribute;
import static uk.ac.ox.poseidon.fishing.vessels.values.attributes.Factories.stringAttribute;
import static uk.ac.ox.poseidon.geography.Factories.coordinate;
import static uk.ac.ox.poseidon.geography.bathymetry.Factories.bathymetricGridFromElevationValues;
import static uk.ac.ox.poseidon.geography.distance.Factories.haversineDistanceCalculator;
import static uk.ac.ox.poseidon.geography.grids.Factories.modelGrid;
import static uk.ac.ox.poseidon.geography.paths.Factories.pathFinder;
import static uk.ac.ox.poseidon.geography.ports.Factories.port;
import static uk.ac.ox.poseidon.geography.ports.Factories.portGrid;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvString;

import com.google.common.collect.Streams;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.measure.Quantity;
import javax.measure.quantity.Mass;
import uk.ac.ox.poseidon.biology.biomass.BiomassGridFactory;
import uk.ac.ox.poseidon.biology.species.SpeciesFactory;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.utils.Pair;

/**
 * A small, hand-built (not YAML-loaded) scenario used by tests: a 3x3 grid, two
 * ports, three species (one generic, two life-staged), two gears, two markets,
 * and a four-vessel fleet created from an inline vessel register. Species {@code C} is
 * discarded rather than retained (see {@link #RETAINED_SPECIES_CODES}), to
 * exercise the disposition path.
 */
@SuppressWarnings("UnstableApiUsage")
public class MinimalScenario implements Supplier<Scenario> {

  public static final LocalDate START_DATE = LocalDate.of(2000, 1, 1);
  public static final List<String> SPECIES_CODES = List.of("A", "B", "C");
  /**
   * All species but the last are retained when caught; the last ({@code C}) is
   * discarded.
   */
  public static final List<String> RETAINED_SPECIES_CODES =
    SPECIES_CODES.subList(0, SPECIES_CODES.size() - 1);
  public static final List<String> LIFE_STAGES = List.of("juvenile", "adult");

  /**
   * One (species code, life stage) pair per configured species: the first
   * species is split into one pair per life stage, every other species gets a
   * single generic ({@code null} life stage) pair.
   */
  public static final List<Pair<String, String>> LIFE_STAGE_PER_SPECIES_CODE =
    Streams.zip(
      // this is a just very roundabout way to generate Stream.of("A", "A", "B",
      // "C")...
      Stream.concat(
        Stream.generate(SPECIES_CODES::getFirst).limit(LIFE_STAGES.size()),
        SPECIES_CODES.stream().skip(1)
      ),
      Stream.concat(LIFE_STAGES.stream(), Stream.generate(() -> null)),
      Pair::of
    ).toList();

  public static final List<String> GEAR_CODES = List.of("G1", "G2");
  public static final List<String> MARKET_CODES = List.of("M1", "M2");
  public static final int NUM_PRICES = GEAR_CODES.size() * SPECIES_CODES.size();
  public static final Quantity<Mass> CARRYING_CAPACITY = getQuantity(1, TONNE);

  /**
   * @return a freshly built minimal {@link Scenario}.
   */
  @Override
  public Scenario get() {
    final var modelGrid = modelGrid(1, -1.5, 1.5, -1.5, 1.5);
    final var bathymetricGrid = bathymetricGridFromElevationValues(
      modelGrid,
      List.of(-1, -1, 0, -1, -1, 0, -1, -1, 0)
    );

    final var carryingCapacityGrid = uniformCarryingCapacityGrid(
      modelGrid,
      bathymetricGrid,
      massOf(CARRYING_CAPACITY)
    );

    final var biomassAllocator = fullCarryingCapacityAllocator(
      carryingCapacityGrid
    );

    // One species per (code, life stage) pair, each with its own biomass grid
    final List<SpeciesFactory> speciesList =
      LIFE_STAGE_PER_SPECIES_CODE.stream()
        .map(codeAndLifeStage ->
          species(
            codeAndLifeStage.getFirst(),
            null,
            codeAndLifeStage.getSecond()
          )
        )
        .toList();
    final var species = listOf(speciesList.toArray(new SpeciesFactory[0]));
    final var biomassGrids = listOf(
      speciesList
        .stream()
        .map(oneSpecies -> biomassGrid(modelGrid, oneSpecies, biomassAllocator))
        .toArray(BiomassGridFactory[]::new)
    );

    final var distance = haversineDistanceCalculator(modelGrid);

    final var port1 = port("P1", "Port 1");
    final var port2 = port("P2", "Port 2");

    final var portGrid = portGrid(
      listOf(pair(port1, coordinate(1, 1)), pair(port2, coordinate(1, -1))),
      bathymetricGrid,
      distance
    );

    // At each port, one price per (gear's catch category, species) pair: the
    // first port's start at 1, the second's at 2, each 0.1 above the last
    final var priceEntries = Stream.of(1, 2)
      .map(portIndex ->
        listOf(
          range(0, NUM_PRICES)
            .mapToObj(i ->
              priceEntry(
                catchCategory(GEAR_CODES.get(i / SPECIES_CODES.size())),
                species(SPECIES_CODES.get(i % SPECIES_CODES.size())),
                price(portIndex + i * 0.1, "GBP", "kg")
              )
            )
            .toList()
        )
      )
      .toList();

    final var market1 = biomassMarket(port1, MARKET_CODES.get(0), "GBP");
    final var market2 = biomassMarket(port2, MARKET_CODES.get(1), "GBP");
    final var markets = listOf(market1, market2);

    final var initialPrices = initialPrices(
      marketPrices(market1, priceEntries.get(0)),
      marketPrices(market2, priceEntries.get(1))
    );

    final var marketGrid = marketGrid(portGrid, markets);

    final var vesselField = vesselField(modelGrid);
    final var pathFinder = pathFinder(bathymetricGrid, portGrid, distance);

    final var gear = choice(
      by("gear"),
      when("G1").then(
        fixedBiomassProportionGear(
          0.25,
          massOf(1, KILOGRAM),
          constant(hours(1)),
          volumetricFlowRateOf(0.0, LITRE_PER_HOUR)
        )
      ),
      when("G2").then(
        fixedBiomassProportionGear(
          0.5,
          massOf(1, KILOGRAM),
          constant(hours(1)),
          volumetricFlowRateOf(0.0, LITRE_PER_HOUR)
        )
      )
    );

    // Each gear's catch is sold under a catch category named after the gear
    final var hold = choice(
      by("gear"),
      when("G1").then(
        infiniteBiomassHold(uniformCatchCategoriser(catchCategory("G1")))
      ),
      when("G2").then(
        infiniteBiomassHold(uniformCatchCategoriser(catchCategory("G2")))
      )
    );

    final var engine = vesselComponent(
      simpleEngine(
        speedOf(10, KNOT),
        volumeOf(0, LITRE) // don't consume any fuel
      )
    );

    final var fuelTank = vesselComponent(fullTank(volumeOf(100000, LITRE)));

    final var travelToTripDestination = travelAlongPathTo(
      engine,
      fuelTank,
      pathFinder,
      distance,
      currentTripDestination(),
      currentTripEventManager()
    );

    // Each vessel fishes at its favourite spot, a coordinate of the register
    final var behaviour = behaviour(
      fishingTrip(
        destinationAt(
          modelGrid,
          doubleAttribute("fav_lon"),
          doubleAttribute("fav_lat")
        ),
        travelToTripDestination,
        fishing(
          gear,
          hold,
          fuelTank,
          currentCellFisheable(fisheableBiomassGrids(biomassGrids)),
          compositeDispositionProcess(
            selectedSpeciesRetention(
              speciesByCode(object(RETAINED_SPECIES_CODES), species)
            ),
            discardMortality(always(0.1))
          )
        ),
        setDestinationToOrigin(),
        travelToTripDestination,
        dockAtDestination(),
        landCatches(hold, hours(1))
      )
    );

    final var fleet = fleet(vesselField, portGrid, marketGrid);

    final var vesselSpecification = vesselSpecification(fleet, behaviour)
      .homePortAttribute("place_of_registration")
      .nameAttribute("name_of_vessel")
      .component("gear", gear)
      .component("hold", hold)
      .component("engine", engine)
      .component("fuelTank", fuelTank)
      .build();

    final var vesselRegister = scheduledByDateTime(
      vesselRegister(
        tableFromCsvString(
          """
          cfr,name_of_vessel,place_of_registration,country_of_registration,loa,event,event_start_date,gear,fav_lon,fav_lat
          V1,Vessel 1,P1,GBR,10,CEN,2000-01-01,G1,-1,-1
          V2,Vessel 2,P1,GBR,10,CEN,2000-01-01,G2,0,0
          V3,Vessel 3,P2,GBR,15,CEN,2000-01-01,G1,0,0
          V4,Vessel 4,P2,GBR,15,CEN,2000-01-01,G2,-1,-1
          """
        ),
        vesselSpecification
      )
    );

    final var fishingActionAccumulator = fishingEventAccumulator();
    final var biomassSaleAccumulator = biomassSaleAccumulator();

    return Scenario.builder()
      .startingDateTime(startOf(START_DATE))
      .component("modelGrid", modelGrid)
      .component("species", species)
      .component("biomassGrids", biomassGrids)
      .component("bathymetricGrid", bathymetricGrid)
      .component("carryingCapacityGrid", carryingCapacityGrid)
      .component("fleet", fleet)
      .component("vesselSpecification", vesselSpecification)
      .component("vesselRegister", vesselRegister)
      .component("vesselField", vesselField)
      .component("portGrid", portGrid)
      .component("markets", markets)
      .component("marketGrid", marketGrid)
      .component("initialPrices", initialPrices)
      .component("fishingActionAccumulator", fishingActionAccumulator)
      .component("biomassSaleAccumulator", biomassSaleAccumulator)
      .component(
        "fleetSegmentMapper",
        fleetSegmentMapper(
          stringAttribute("gear"),
          stringAttribute("country_of_registration"),
          intervalLabel(
            doubleAttribute("loa"),
            interval(0.0, 12.0, "VL0012"),
            interval(12.0, null, "VL12XX")
          ),
          "Industrial"
        )
      )
      .build();
  }
}
