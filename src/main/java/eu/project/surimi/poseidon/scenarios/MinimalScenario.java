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
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.ScenarioSupplier;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGridFromElevationValuesFactory;
import uk.ac.ox.poseidon.geography.grids.ModelGridFactory;

import java.util.List;

@Getter
@Setter
public class MinimalScenario extends ScenarioSupplier {

    private Factory<? extends BathymetricGrid> bathymetricGrid =
        new BathymetricGridFromElevationValuesFactory(
            new ModelGridFactory(
                1,
                -1.5, 1.5, -1.5, 1.5
            ),
            List.of(
                -1, -1, 0,
                -1, -1, 0,
                -1, -1, 0
            )
        );

}
