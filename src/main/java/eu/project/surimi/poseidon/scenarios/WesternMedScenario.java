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

import eu.project.surimi.poseidon.components.FleetIdRegister;
import eu.project.surimi.poseidon.components.FleetIdRegisterFactory;
import lombok.Getter;
import lombok.Setter;
import sim.engine.Steppable;
import sim.util.Int2D;
import uk.ac.ox.poseidon.agents.behaviours.BehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.WaitingBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.choices.BestOptionsFromFriendsSupplierFactory;
import uk.ac.ox.poseidon.agents.behaviours.choices.ExponentialMovingAverageOptionValuesFactory;
import uk.ac.ox.poseidon.agents.behaviours.choices.MutableOptionValues;
import uk.ac.ox.poseidon.agents.behaviours.destination.*;
import uk.ac.ox.poseidon.agents.behaviours.disposition.CompositeDispositionProcessFactory;
import uk.ac.ox.poseidon.agents.behaviours.disposition.GeneralDiscardMortalityFactory;
import uk.ac.ox.poseidon.agents.behaviours.disposition.ProportionallyLimitingBiomassToHoldFactory;
import uk.ac.ox.poseidon.agents.behaviours.disposition.SelectedSpeciesRetentionFactory;
import uk.ac.ox.poseidon.agents.behaviours.fishing.DefaultFishingBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.fishing.FishingActionAccumulator;
import uk.ac.ox.poseidon.agents.behaviours.fishing.FishingActionAccumulatorFactory;
import uk.ac.ox.poseidon.agents.behaviours.port.HomeBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.port.LandingBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.strategy.ThereAndBackBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.travel.TravellingAlongPathBehaviourFactory;
import uk.ac.ox.poseidon.agents.fields.VesselField;
import uk.ac.ox.poseidon.agents.fields.VesselFieldFactory;
import uk.ac.ox.poseidon.agents.fisheables.CurrentCellFisheableFactory;
import uk.ac.ox.poseidon.agents.market.*;
import uk.ac.ox.poseidon.agents.registers.DynamicRegisterFactory;
import uk.ac.ox.poseidon.agents.registers.ImmutableRegisterFactory;
import uk.ac.ox.poseidon.agents.registers.Register;
import uk.ac.ox.poseidon.agents.regulations.*;
import uk.ac.ox.poseidon.agents.tables.FishingActionListenerTableFactory;
import uk.ac.ox.poseidon.agents.vessels.*;
import uk.ac.ox.poseidon.agents.vessels.gears.FishingGear;
import uk.ac.ox.poseidon.agents.vessels.gears.FixedBiomassProportionGearFactory;
import uk.ac.ox.poseidon.agents.vessels.hold.Hold;
import uk.ac.ox.poseidon.agents.vessels.hold.StandardBiomassHoldFactory;
import uk.ac.ox.poseidon.biology.biomass.*;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.biology.species.SpeciesByCodeFactory;
import uk.ac.ox.poseidon.biology.species.SpeciesFromFileFactory;
import uk.ac.ox.poseidon.core.*;
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
import uk.ac.ox.poseidon.core.suppliers.ConstantDoubleSupplierFactory;
import uk.ac.ox.poseidon.core.suppliers.PoissonIntSupplierFactory;
import uk.ac.ox.poseidon.core.suppliers.ShiftedIntSupplierFactory;
import uk.ac.ox.poseidon.core.suppliers.temporal.DurationUntilSupplierFactory;
import uk.ac.ox.poseidon.core.suppliers.temporal.NextDayAtTimeSupplierFactory;
import uk.ac.ox.poseidon.core.time.DateTimeAfterStartingFactory;
import uk.ac.ox.poseidon.core.time.TimeFactory;
import uk.ac.ox.poseidon.core.utils.ConstantFactory;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGridFromGridFileFactory;
import uk.ac.ox.poseidon.geography.bathymetry.adaptors.CellElevationFactory;
import uk.ac.ox.poseidon.geography.distance.DistanceCalculator;
import uk.ac.ox.poseidon.geography.distance.HaversineDistanceCalculatorFactory;
import uk.ac.ox.poseidon.geography.grids.CellSetFromGridFileFactory;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;
import uk.ac.ox.poseidon.geography.grids.ModelGridWithActiveCellsFromGridFile;
import uk.ac.ox.poseidon.geography.paths.DefaultPathFinderFactory;
import uk.ac.ox.poseidon.geography.paths.GridPathFinder;
import uk.ac.ox.poseidon.geography.ports.PortGrid;
import uk.ac.ox.poseidon.geography.ports.PortGridFromFileFactory;
import uk.ac.ox.poseidon.io.DirectoryRemoverFactory;
import uk.ac.ox.poseidon.io.ScenarioWriter;
import uk.ac.ox.poseidon.io.paths.PathFactory;
import uk.ac.ox.poseidon.io.tables.CsvTableWriterFactory;
import uk.ac.ox.poseidon.regulations.ForbiddenIfFactory;
import uk.ac.ox.poseidon.regulations.predicates.spatial.ActionCellPredicateFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import static java.time.DayOfWeek.*;
import static java.util.stream.IntStream.range;
import static uk.ac.ox.poseidon.core.suppliers.ConstantDurationSuppliers.ONE_DAY_DURATION_SUPPLIER;
import static uk.ac.ox.poseidon.core.suppliers.ConstantDurationSuppliers.ONE_HOUR_DURATION_SUPPLIER;
import static uk.ac.ox.poseidon.core.time.PeriodFactory.DAILY;
import static uk.ac.ox.poseidon.core.time.PeriodFactory.MONTHLY;

@Getter
@Setter
public class WesternMedScenario extends ScenarioSupplier {

    private static final double DIFFERENTIAL_PERCENTAGE_TO_MOVE = 0.05;
    private static final double PERCENTAGE_LIMIT_ON_DAILY_MOVEMENT = 0.1;
    private static final double LOGISTIC_GROWTH_RATE = 0.00001;
    private static final String CARRYING_CAPACITY = "10 kg";
    private static final double LEARNING_ALPHA = 1;
    private static final double EXPLORATION_PROBABILITY = 0.2;
    private static final int MEAN_EXPLORATION_RADIUS = 1;
    private static final double CATCH_PROPORTION = 0.1;
    private static final String VESSEL_SPEED = "9.5 kn"; // as per email on 2025-03-18 08:20
    private static final String VESSEL_HOLD_CAPACITY = "1 t";
    private static final String FLEET_ID = "F0";

    public WesternMedScenario() {
        super(LocalDate.of(2013, 1, 1));
    }

    private Factory<? extends BiomassGrowthRule> biomassGrowthRule =
        new LogisticGrowthRuleFactory(LOGISTIC_GROWTH_RATE);
    private Factory<? extends BiomassDiffusionRule> biomassDiffusionRule =
        new SmoothBiomassDiffusionRuleFactory(
            DIFFERENTIAL_PERCENTAGE_TO_MOVE,
            PERCENTAGE_LIMIT_ON_DAILY_MOVEMENT
        );
    private PathFactory rootPath = PathFactory.of("western_med");
    private PathFactory inputPath = rootPath.plus("data");
    private PathFactory outputPath = rootPath.plus("outputs").simulationFolder();
    private GlobalScopeFactory<? extends ModelGrid> modelGrid =
        new ModelGridWithActiveCellsFromGridFile(
            new CellSetFromGridFileFactory(
                inputPath.plus("exclusion_grid.asc"),
                0
            )
        );
    private Factory<? extends BathymetricGrid> bathymetricGrid =
        new BathymetricGridFromGridFileFactory(
            inputPath.plus("bathymetry_grid.asc"),
            modelGrid,
            new MaxFactory(),
            false
        );
    private Factory<? extends CarryingCapacityGrid> carryingCapacityGrid =
        new UniformCarryingCapacityGridFactory(
            bathymetricGrid,
            MassFactory.of(CARRYING_CAPACITY)
        );
    private Factory<? extends BiomassAllocator> biomassAllocator =
        new FullBiomassAllocatorFactory(carryingCapacityGrid);
    @SuppressWarnings("MagicNumber")
    private Factory<? extends Regulations> regulations =
        new ForbiddenIfFactory(
            new AnyOfFactory<>(
                new ActionCellPredicateFactory(
                    modelGrid,
                    new AdaptedPredicateFactory<>(
                        new CellElevationFactory(bathymetricGrid),
                        new AboveFactory(-35)
                    )
                ),
                new ActionCellPredicateFactory(
                    modelGrid,
                    new InSetFactory<>(
                        new CellSetFromGridFileFactory(
                            inputPath.plus("french_eez.asc"),
                            1
                        )
                    )
                )
            )
        );
    private Factory<? extends VesselField> vesselField = new VesselFieldFactory(modelGrid);
    private Factory<? extends DistanceCalculator> distance =
        new HaversineDistanceCalculatorFactory(modelGrid);
    private Factory<? extends PortGrid> portGrid =
        new PortGridFromFileFactory(
            bathymetricGrid,
            distance,
            inputPath.plus("ports.csv"),
            "port_code", "port_name", "lon", "lat"
        );
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
    private VesselScopeFactory<? extends FishingLocationLegalityChecker> fishingLocationChecker =
        new FishingLocationLegalityCheckerFactory(
            regulations,
            pathFinder,
            distance
        );
    private Factory<? extends FishingGear<Biomass>> fishingGear =
        new FixedBiomassProportionGearFactory(
            "PS",
            CATCH_PROPORTION,
            ONE_HOUR_DURATION_SUPPLIER
        );
    private VesselScopeFactory<? extends GearSpecificFishingLocationLegalityChecker>
        gearSpecificFishingLocationChecker =
        new GearSpecificFishingLocationLegalityCheckerFactory(fishingGear, fishingLocationChecker);
    private Factory<? extends List<Species>> species =
        new SpeciesFromFileFactory(
            inputPath.plus("species.csv"),
            "species_code",
            "species_name"
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
    private Factory<? extends BiomassSaleAccumulator> biomassSaleAccumulator =
        new BiomassSaleAccumulatorFactory();
    private Factory<? extends FishingActionAccumulator> fishingActionAccumulator =
        new FishingActionAccumulatorFactory();
    private Factory<? extends Steppable> dailyProcesses =
        new ScheduledRepeatingFactory<>(
            new DateTimeAfterStartingFactory(DAILY),
            DAILY,
            new SteppableSequenceFactory(
                new MappedFactory<>(
                    new BiomassDiffuserFactory(
                        null,
                        carryingCapacityGrid,
                        biomassDiffusionRule
                    ),
                    "biomassGrid",
                    biomassGrids
                )
            ),
            -1
        );
    private Factory<? extends Steppable> monthlyProcesses =
        new ScheduledRepeatingFactory<>(
            new DateTimeAfterStartingFactory(MONTHLY),
            MONTHLY,
            new SteppableSequenceFactory(
                new SteppableSequenceFactory(
                    new MappedFactory<>(
                        new BiomassGrowerFactory(
                            null,
                            carryingCapacityGrid,
                            biomassGrowthRule
                        ),
                        "biomassGrid",
                        biomassGrids
                    )
                ),
                new EventClearerFactory(biomassSaleAccumulator),
                new EventClearerFactory(fishingActionAccumulator),
                new CsvTableWriterFactory(
                    new FishingActionListenerTableFactory(),
                    outputPath.plus("fishing_actions.csv"),
                    true,
                    true
                )
            ),
            -2
        );
    private Factory<? extends MarketGrid<Biomass, ? extends Market<Biomass>>> marketGrid =
        new BiomassMarketGridPriceFileFactory(
            inputPath.plus("prices.csv"),
            "market_code",
            "species_code",
            "price",
            "currency",
            "measurement_unit",
            portGrid,
            species
        );
    private VesselScopeFactory<? extends Hold<Biomass>> hold = new StandardBiomassHoldFactory(
        MassFactory.of(VESSEL_HOLD_CAPACITY),
        MassFactory.of("1 kg")
    );
    private VesselScopeFactory<? extends MutableOptionValues<Int2D>> optionValues =
        new ExponentialMovingAverageOptionValuesFactory<>(LEARNING_ALPHA);
    private Factory<? extends Register<MutableOptionValues<Int2D>>> optionValuesRegister =
        new DynamicRegisterFactory<>(optionValues);
    private Factory<List<Vessel>> vessels =
        new VesselsFromFileFactory(
            inputPath.plus("vessels.csv"),
            "vessel_id",
            "vessel_name",
            "port_code",
            new HomeBehaviourFactory(
                portGrid,
                hold,
                new AllOfFactory<>(
                    new AdaptedVesselPredicateFactory<>(
                        new CurrentTimeFactory(),
                        new TimeIsAfterFactory(new TimeFactory(21, 59, 59))
                    ),
                    new AdaptedVesselPredicateFactory<>(
                        new CurrentDayOfWeekFactory(),
                        new InSetFactory<>(
                            SUNDAY,
                            MONDAY,
                            TUESDAY,
                            WEDNESDAY,
                            THURSDAY
                        )
                    )
                ),
                new ThereAndBackBehaviourFactory(
                    new ChoosingDestinationBehaviourFactory(
                        new EpsilonGreedyDestinationSupplierFactory(
                            EXPLORATION_PROBABILITY,
                            optionValues,
                            new NeighbourhoodGridExplorerFactory(
                                optionValues,
                                gearSpecificFishingLocationChecker,
                                pathFinder,
                                new ShiftedIntSupplierFactory(
                                    new PoissonIntSupplierFactory(MEAN_EXPLORATION_RADIUS),
                                    1
                                )
                            ),
                            new ImitatingPickerFactory<>(
                                optionValues,
                                gearSpecificFishingLocationChecker,
                                new BestOptionsFromFriendsSupplierFactory<>(
                                    5,
                                    optionValuesRegister
                                )
                            ),
                            new TotalBiomassCaughtPerHourDestinationEvaluatorFactory(portGrid)
                        ),
                        ONE_HOUR_DURATION_SUPPLIER,
                        new WaitingBehaviourFactory(ONE_DAY_DURATION_SUPPLIER)
                    ),
                    new DefaultFishingBehaviourFactory<>(
                        fishingGear,
                        hold,
                        new CurrentCellFisheableFactory<>(
                            new BiomassGridsFactory(
                                biomassGrids
                            )
                        ),
                        regulations,
                        new CompositeDispositionProcessFactory<>(
                            new SelectedSpeciesRetentionFactory<Biomass>(
                                new SpeciesByCodeFactory(
                                    new ConstantFactory<>(List.of("PIL", "ANE")),
                                    // TODO: we're currently restricting to a couple of species code
                                    //  for testing,  but, once we have the complete list of species
                                    //  for the  model (and not just the ones for which we currently
                                    //  have prices) we should (probably?) restrict to species
                                    //  that have a monetary value:
                                    // new StringColumnReaderFactory(inputPath.plus("prices.csv")
                                    // , "species_id"),
                                    species
                                )
                            ),
                            new ProportionallyLimitingBiomassToHoldFactory(),
                            new GeneralDiscardMortalityFactory(
                                new ConstantDoubleSupplierFactory(0.1)
                            )
                        )
                    ),
                    travellingBehaviour
                ),
                new WaitingBehaviourFactory(
                    new DurationUntilSupplierFactory(
                        new NextDayAtTimeSupplierFactory(
                            new TimeFactory(22, 0, 0)
                        )
                    )
                ),
                travellingBehaviour,
                new LandingBehaviourFactory<>(marketGrid, hold, ONE_HOUR_DURATION_SUPPLIER)
            ),
            vesselField,
            portGrid,
            SpeedFactory.of(VESSEL_SPEED),
            "EUR"
        );
    private Factory<? extends FleetIdRegister> fleetIdRegister =
        new FleetIdRegisterFactory(
            new ImmutableRegisterFactory<>(
                vessels,
                new VesselScopeAdaptor<>(new ConstantFactory<>(FLEET_ID))
            )
        );

    private Factory<Steppable> directoryRemover =
        new FinalProcessFactory<>(
            new DirectoryRemoverFactory(outputPath, false)
        );

    public static void main(final String[] args) {
        final int numSteps = 12;
        final Period stepSize = Period.ofMonths(1);
        final Scenario scenario = new WesternMedScenario().get();
        final Path scenarioPath = Path.of("western_med", "scenario.yaml");
        new ScenarioWriter().write(scenario, scenarioPath);
        final Simulation simulation = scenario.newSimulation();
        simulation.start();
        final TemporalSchedule temporalSchedule = simulation.getTemporalSchedule();
        range(0, numSteps).forEach(__ ->
            temporalSchedule.stepFor(simulation, stepSize)
        );
        simulation.finish();
    }
}
