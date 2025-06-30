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
import uk.ac.ox.poseidon.agents.behaviours.WaitingBehaviourFactory;
import uk.ac.ox.poseidon.agents.fields.VesselField;
import uk.ac.ox.poseidon.agents.fields.VesselFieldFactory;
import uk.ac.ox.poseidon.agents.market.Market;
import uk.ac.ox.poseidon.agents.market.MarketGrid;
import uk.ac.ox.poseidon.agents.market.OneMarketPerPortBiomassMarketGridFactory;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.VesselFactory;
import uk.ac.ox.poseidon.biology.biomass.*;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.biology.species.SpeciesFactory;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.ListFactory;
import uk.ac.ox.poseidon.core.MappedFactory;
import uk.ac.ox.poseidon.core.ScenarioSupplier;
import uk.ac.ox.poseidon.core.quantities.MassFactory;
import uk.ac.ox.poseidon.core.quantities.SpeedFactory;
import uk.ac.ox.poseidon.core.time.DateFactory;
import uk.ac.ox.poseidon.geography.CoordinateFactory;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGridFromElevationValuesFactory;
import uk.ac.ox.poseidon.geography.distance.DistanceCalculator;
import uk.ac.ox.poseidon.geography.distance.HaversineDistanceCalculatorFactory;
import uk.ac.ox.poseidon.geography.grids.ModelGridFactory;
import uk.ac.ox.poseidon.geography.ports.Port;
import uk.ac.ox.poseidon.geography.ports.PortFactory;
import uk.ac.ox.poseidon.geography.ports.PortGrid;
import uk.ac.ox.poseidon.geography.ports.PortGridFactory;

import java.util.List;

import static uk.ac.ox.poseidon.core.suppliers.ConstantDurationSuppliers.ONE_DAY_DURATION_SUPPLIER;

@Getter
@Setter
public class MinimalScenario extends ScenarioSupplier {

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
            MassFactory.of("1 t")
        );
    private Factory<? extends BiomassAllocator> biomassAllocator =
        new FullBiomassAllocatorFactory(carryingCapacityGrid);
    private Factory<? extends List<? extends Species>> species =
        new ListFactory<>(
            new SpeciesFactory("A"),
            new SpeciesFactory("B")
        );
    private Factory<List<BiomassGrid>> biomassGrids =
        new MappedFactory<>(
            new BiomassGridFactory(
                modelGrid,
                null,
                biomassAllocator
            ),
            species,
            "species"
        );
    private Factory<? extends PortGrid> portGrid =
        new PortGridFactory(bathymetricGrid);
    private Factory<? extends Port> port1 =
        new PortFactory(portGrid, "P1", "Port 1", new CoordinateFactory(1, 1));
    private Factory<? extends Port> port2 =
        new PortFactory(portGrid, "P2", "Port 2", new CoordinateFactory(1, -1));
    private Factory<? extends MarketGrid<Biomass, ? extends Market<Biomass>>> marketGrid =
        new OneMarketPerPortBiomassMarketGridFactory(portGrid);
    private Factory<? extends VesselField> vesselField =
        new VesselFieldFactory(modelGrid);
    private Factory<? extends Vessel> vessel1 =
        new VesselFactory(
            new WaitingBehaviourFactory(ONE_DAY_DURATION_SUPPLIER),
            "V1",
            "Vessel 1",
            vesselField,
            port1,
            portGrid,
            SpeedFactory.of("10 kn"),
            "EUR"
        );
    private Factory<? extends DistanceCalculator> distance =
        new HaversineDistanceCalculatorFactory(modelGrid);

    public MinimalScenario() {
        super(new DateFactory(2000, 1, 1));
    }
}
