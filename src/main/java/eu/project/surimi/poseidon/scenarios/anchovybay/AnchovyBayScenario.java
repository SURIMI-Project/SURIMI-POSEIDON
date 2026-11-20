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

package eu.project.surimi.poseidon.scenarios.anchovybay;

import static uk.ac.ox.poseidon.biology.biomass.Factories.biomassGrids;
import static uk.ac.ox.poseidon.biology.species.Factories.speciesFromData;
import static uk.ac.ox.poseidon.core.aggregators.Factories.maxAggregator;
import static uk.ac.ox.poseidon.core.time.Factories.startOf;
import static uk.ac.ox.poseidon.core.utils.Factories.listOf;
import static uk.ac.ox.poseidon.fishing.market.Factories.marketGrid;
import static uk.ac.ox.poseidon.fishing.market.Factories.oneBiomassMarketPerPort;
import static uk.ac.ox.poseidon.geography.allocators.Factories.filteredAllocator;
import static uk.ac.ox.poseidon.geography.allocators.Factories.supplierAllocator;
import static uk.ac.ox.poseidon.geography.bathymetry.Factories.bathymetricGridFromGridFile;
import static uk.ac.ox.poseidon.geography.distance.Factories.haversineDistanceCalculator;
import static uk.ac.ox.poseidon.geography.grids.Factories.cellSetFromGridFile;
import static uk.ac.ox.poseidon.geography.grids.Factories.modelGridFromGridFile;
import static uk.ac.ox.poseidon.geography.grids.Factories.modelGridWithActiveCells;
import static uk.ac.ox.poseidon.geography.ports.Factories.portGrid;
import static uk.ac.ox.poseidon.geography.ports.Factories.portsFromTable;
import static uk.ac.ox.poseidon.geography.predicates.Factories.isActiveWaterCell;
import static uk.ac.ox.poseidon.io.paths.Factories.path;
import static uk.ac.ox.poseidon.io.tables.Factories.tableFromCsvFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Supplier;
import uk.ac.ox.poseidon.core.Scenario;

/**
 * The SURIMI Anchovy Bay scenario, built from the pre-processing pipeline's
 * files in {@code inputs/anchovy_bay/}: the grid, active cells, bathymetry,
 * species and ports, with one market per port. Biomass starts at zero on
 * active water cells; biomass and market prices arrive through the controller.
 * The {@code writeAnchovyBayScenario} Gradle task writes it to
 * {@code scenarios/anchovy_bay.yaml}. The server does not serve it.
 */
public class AnchovyBayScenario implements Supplier<Scenario> {

  /** The start date of the Anchovy Bay simulation. */
  // EwE keeps no start date in an Ecosim scenario. It dates a run from the
  // first year of the time series dataset in use. The model's three datasets
  // all start in 1970. The Ecopath model's own first year, 2000, is not used.
  public static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);
  static final Path INPUT_PATH = Path.of("inputs", "anchovy_bay");

  /**
   * @return a freshly built Anchovy Bay {@link Scenario}.
   */
  @Override
  public Scenario get() {
    final var inputPath = path(INPUT_PATH);
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
    final var portGrid = portGrid(
      portsFromTable(
        tableFromCsvFile(inputPath.plus("ports.csv")),
        "port_code",
        "port_name",
        "lon",
        "lat"
      ),
      bathymetricGrid,
      haversineDistanceCalculator(modelGrid)
    );
    final var marketGrid = marketGrid(
      portGrid,
      oneBiomassMarketPerPort(portGrid, "EUR")
    );
    final var species = speciesFromData(
      tableFromCsvFile(inputPath.plus("species.csv")),
      "species_code",
      "species_name",
      "life_stage"
    );
    final var zeroBiomassAllocator = filteredAllocator(
      supplierAllocator(0.0),
      isActiveWaterCell(bathymetricGrid)
    );
    final var biomassGrids = biomassGrids(
      modelGrid,
      species,
      listOf(zeroBiomassAllocator)
    );

    return Scenario.builder()
      .startingDateTime(startOf(START_DATE))
      .component("modelGrid", modelGrid)
      .component("bathymetricGrid", bathymetricGrid)
      .component("portGrid", portGrid)
      .component("marketGrid", marketGrid)
      .component("species", species)
      .component("biomassGrids", biomassGrids)
      .build();
  }
}
