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
import uk.ac.ox.poseidon.agents.behaviours.port.HomeBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.port.LandingBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.strategy.ThereAndBackBehaviourFactory;
import uk.ac.ox.poseidon.agents.behaviours.travel.TravellingAlongPathBehaviourFactory;
import uk.ac.ox.poseidon.agents.fields.VesselField;
import uk.ac.ox.poseidon.agents.fields.VesselFieldFactory;
import uk.ac.ox.poseidon.agents.fisheables.CurrentCellFisheableFactory;
import uk.ac.ox.poseidon.agents.market.*;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.VesselFactory;
import uk.ac.ox.poseidon.agents.vessels.VesselScopeFactory;
import uk.ac.ox.poseidon.agents.vessels.gears.FishingGear;
import uk.ac.ox.poseidon.agents.vessels.gears.FixedBiomassProportionGearFactory;
import uk.ac.ox.poseidon.agents.vessels.hold.Hold;
import uk.ac.ox.poseidon.agents.vessels.hold.InfiniteBiomassHoldFactory;
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
import uk.ac.ox.poseidon.core.time.DateFactory;
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
import uk.ac.ox.poseidon.regulations.PermittedIfFactory;

import javax.measure.Quantity;
import javax.measure.quantity.Mass;
import java.util.List;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static si.uom.NonSI.TONNE;
import static tech.units.indriya.quantity.Quantities.getQuantity;
import static uk.ac.ox.poseidon.core.suppliers.ConstantDurationSuppliers.*;

@Getter
@Setter
public class MinimalScenario extends ScenarioSupplier {

    public static final List<String> SPECIES_CODES = List.of("A", "B", "C");
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
            "code",
            new ConstantFactory<>(SPECIES_CODES)
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
                new PricePerSpeciesFactory(
                    species,
                    range(0, SPECIES_CODES.size()).boxed().collect(toMap(
                        SPECIES_CODES::get,
                        i -> new PriceFactory((i + 1) * 10, "GBP", "kg")
                    ))
                )
            ),
            "portCode",
            new ConstantFactory<>(List.of("P1", "P2"))
        );
    private Factory<? extends VesselField> vesselField =
        new VesselFieldFactory(modelGrid);
    private VesselScopeFactory<? extends Hold<Biomass>> hold =
        new InfiniteBiomassHoldFactory();
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

    private VesselScopeFactory<? extends FishingGear<Biomass>> gear1 =
        new FixedBiomassProportionGearFactory(
            "G1",
            0.25,
            ONE_HOUR_DURATION_SUPPLIER
        );

    private VesselScopeFactory<? extends FishingGear<Biomass>> gear2 =
        new FixedBiomassProportionGearFactory(
            "G2",
            0.5,
            ONE_HOUR_DURATION_SUPPLIER
        );

    private Factory<List<Vessel>> vessels =
        new MappedFactory<>(
            new VesselFactory(
                new HomeBehaviourFactory(
                    portGrid,
                    hold,
                    new AlwaysTrueFactory(),
                    new ThereAndBackBehaviourFactory(
                        new ChoosingDestinationBehaviourFactory(
                            new ConstantDestinationSupplierFactory(
                                modelGrid,
                                null
                            ),
                            ONE_MINUTE_DURATION_SUPPLIER,
                            new WaitingBehaviourFactory(ONE_SECOND_DURATION_SUPPLIER)
                        ),
                        new DefaultFishingBehaviourFactory<>(
                            null,
                            hold,
                            new CurrentCellFisheableFactory<>(
                                new BiomassGridsFactory(
                                    biomassGrids
                                )
                            ),
                            new PermittedIfFactory(new AlwaysTrueFactory()),
                            new CompositeDispositionProcessFactory<>(
                                new SelectedSpeciesRetentionFactory<Biomass>(
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
                    new LandingBehaviourFactory<>(marketGrid, hold, ONE_HOUR_DURATION_SUPPLIER)
                ),
                null,
                null,
                vesselField,
                null,
                portGrid,
                SpeedFactory.of("10 kn"),
                "EUR"
            ),
            List.of(
                "id",
                "name",
                "homePort",
                "initialBehaviour.behaviourIfReady.fishingBehaviour.fishingGear",
                "initialBehaviour.behaviourIfReady.fishingDestinationBehaviour" +
                    ".destinationSupplier.coordinate"
            ),
            List.of(
                new ListFactory<>("V1", "V2", "V3", "V4"),
                new ListFactory<>("Vessel 1", "Vessel 2", "Vessel 3", "Vessel 4"),
                new ListFactory<>(port1, port1, port2, port2),
                new ListFactory<>(gear1, gear2, gear1, gear2),
                new ListFactory<>(
                    new CoordinateFactory(-1, 1),
                    new CoordinateFactory(0, 0),
                    new CoordinateFactory(0, 0),
                    new CoordinateFactory(-1, -1)
                )
            )
        );

    public MinimalScenario() {
        super(new DateFactory(2000, 1, 1));
    }
}
