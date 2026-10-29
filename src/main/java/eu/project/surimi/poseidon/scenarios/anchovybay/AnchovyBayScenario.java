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

import static eu.project.surimi.poseidon.ewe.Factories.bathymetricGridFromEcospaceBasemap;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathGroups;
import static eu.project.surimi.poseidon.ewe.Factories.ecopathSpeciesMappingFromGroups;
import static eu.project.surimi.poseidon.ewe.Factories.ecospaceBasemap;
import static eu.project.surimi.poseidon.ewe.Factories.ecospacePortCells;
import static eu.project.surimi.poseidon.ewe.Factories.eweDatabase;
import static eu.project.surimi.poseidon.ewe.Factories.modelGridFromEcospaceBasemap;
import static eu.project.surimi.poseidon.ewe.Factories.speciesFromEcopathSpeciesMapping;
import static uk.ac.ox.poseidon.core.time.Factories.startOf;
import static uk.ac.ox.poseidon.core.utils.Factories.prefixedId;
import static uk.ac.ox.poseidon.geography.distance.Factories.haversineDistanceCalculator;
import static uk.ac.ox.poseidon.geography.ports.Factories.portGrid;
import static uk.ac.ox.poseidon.geography.ports.Factories.portsFromCells;
import static uk.ac.ox.poseidon.io.paths.Factories.path;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Supplier;
import uk.ac.ox.poseidon.core.Scenario;

/**
 * The Anchovy Bay scenario, built from the EwE model in
 * {@code inputs/anchovy_bay/anchovy_bay.eiixml}. For now it has only the model
 * grid, the bathymetry and the ports of the model's "BayOfAnchovies" Ecospace
 * scenario, and the species; fleets and the rest of the biology come later.
 * EwE ports have no names, so each port cell is coded {@code P0}, {@code P1},
 * and so on, in row-major order. Each functional group of the Ecopath model,
 * detritus included, is a species of its own, coded by the group's ID and
 * named after it.
 * The {@code writeAnchovyBayScenario}
 * Gradle task writes it to {@code scenarios/anchovy_bay.yaml}. The server does
 * not serve it.
 */
public class AnchovyBayScenario implements Supplier<Scenario> {

  // EwE keeps no start date in an Ecosim scenario. It dates a run from the
  // first year of the time series dataset in use, here the model's only one,
  // "anchovybay", which starts in 1970. The model's own first year is 0.
  public static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);
  static final Path INPUT_PATH = Path.of("inputs", "anchovy_bay");

  /**
   * @return a freshly built Anchovy Bay {@link Scenario}.
   */
  @Override
  public Scenario get() {
    final var eweDatabase = eweDatabase(
      path(INPUT_PATH).plus("anchovy_bay.eiixml")
    );
    final var ecospaceBasemap = ecospaceBasemap(eweDatabase, "BayOfAnchovies");
    final var modelGrid = modelGridFromEcospaceBasemap(ecospaceBasemap);
    final var bathymetricGrid = bathymetricGridFromEcospaceBasemap(
      ecospaceBasemap,
      modelGrid
    );
    final var portGrid = portGrid(
      portsFromCells(
        ecospacePortCells(eweDatabase, ecospaceBasemap),
        modelGrid,
        prefixedId("P")
      ),
      bathymetricGrid,
      haversineDistanceCalculator(modelGrid)
    );
    final var ecopathSpeciesMapping = ecopathSpeciesMappingFromGroups(
      ecopathGroups(eweDatabase)
    );
    final var species = speciesFromEcopathSpeciesMapping(ecopathSpeciesMapping);

    return Scenario.builder()
      .startingDateTime(startOf(START_DATE))
      .component("modelGrid", modelGrid)
      .component("bathymetricGrid", bathymetricGrid)
      .component("portGrid", portGrid)
      .component("species", species)
      .build();
  }
}
