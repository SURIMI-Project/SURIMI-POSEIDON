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

package eu.project.surimi.poseidon.scenarios.northwesternmed;

import uk.ac.ox.poseidon.core.Scenario;

import java.util.function.Supplier;

import static eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario.INPUT_PATH;
import static uk.ac.ox.poseidon.agents.market.Factories.priceUpdatesFromTable;
import static uk.ac.ox.poseidon.biology.biomass.Factories.timeIndexedBiomassGridUpdates;
import static uk.ac.ox.poseidon.biology.biomass.Factories.timeIndexedBiomassGridsFromNetCdf;
import static uk.ac.ox.poseidon.core.schedule.Factories.scheduledByDateTime;
import static uk.ac.ox.poseidon.io.paths.Factories.path;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvFile;

/**
 * The Northwestern Mediterranean scenario run on its own, outside SURIMI: the
 * {@link NorthwesternMedScenario} served to SURIMI, plus local drivers that read biomass from
 * {@code biomass_grids.nc} and prices from {@code prices.csv}, standing in for the
 * {@code UpdateBiomass} and {@code UpdateSpeciesPrices} messages. Used by the GUI and the
 * calibration.
 * <p>
 * The drivers find the objects they update through the base scenario's named components, so
 * renaming one of those components breaks this scenario at simulation start.
 */
public class LocalNorthwesternMedScenario implements Supplier<Scenario> {

    /**
     * @return a freshly built {@link NorthwesternMedScenario} with the local drivers added.
     */
    @Override
    public Scenario get() {
        final Scenario base = new NorthwesternMedScenario().get();
        final var inputPath = path(INPUT_PATH);
        return base
            .toBuilder()
            .component(
                "biomassUpdates",
                scheduledByDateTime(
                    timeIndexedBiomassGridUpdates(
                        base.component("fisheableBiomassGrids"),
                        timeIndexedBiomassGridsFromNetCdf(
                            base.component("modelGrid"),
                            base.component("species"),
                            inputPath.plus("biomass_grids.nc")
                        )
                    )
                )
            )
            .component(
                "priceUpdates",
                scheduledByDateTime(
                    priceUpdatesFromTable(
                        tableFromCsvFile(inputPath.plus("prices.csv")),
                        "date",
                        "market_code",
                        "species_code",
                        "category_code",
                        "price",
                        "currency",
                        "measurement_unit",
                        base.component("marketGrid"),
                        base.component("species")
                    )
                )
            )
            .build();
    }

}
