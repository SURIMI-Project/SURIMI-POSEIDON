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

package eu.project.surimi.poseidon.scenarios;

import static eu.project.surimi.poseidon.scenarios.anchovybay.AnchovyBayScenario.START_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import eu.project.surimi.poseidon.scenarios.anchovybay.AnchovyBayScenario;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Scanner;
import java.util.function.BiConsumer;
import org.joda.money.CurrencyUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.biology.biomass.BiomassGrid;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.fishing.market.BiomassMarket;
import uk.ac.ox.poseidon.fishing.market.Market;
import uk.ac.ox.poseidon.fishing.market.MarketGrid;
import uk.ac.ox.poseidon.geography.Coordinate;
import uk.ac.ox.poseidon.geography.Envelope;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.grids.Cell;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;
import uk.ac.ox.poseidon.geography.ports.Port;
import uk.ac.ox.poseidon.geography.ports.PortGrid;
import uk.ac.ox.poseidon.io.ScenarioLoader;
import uk.ac.ox.poseidon.io.ScenarioWriter;

// Exercise the YAML round trip used by scenarios/anchovy_bay.yaml. Compare
// against pipeline files, whose contents can change with revisions of the model.
class AnchovyBayScenarioTest {

  private static final Path INPUT_PATH = Path.of("inputs", "anchovy_bay");

  private Simulation simulation;

  private record PipelineRaster(
    int width,
    int height,
    Envelope envelope,
    double[][] values
  ) {
    void forEach(final BiConsumer<Cell, Double> action) {
      for (int x = 0; x < width; x++) {
        for (int y = 0; y < height; y++) {
          action.accept(new Cell(x, y), values[x][y]);
        }
      }
    }
  }

  private static PipelineRaster raster(final String fileName) {
    try (
      final Scanner scanner = new Scanner(
        Files.readString(INPUT_PATH.resolve(fileName))
      )
    ) {
      scanner.useLocale(Locale.ROOT);
      scanner.next();
      final int width = scanner.nextInt();
      scanner.next();
      final int height = scanner.nextInt();
      scanner.next();
      final double west = scanner.nextDouble();
      scanner.next();
      final double south = scanner.nextDouble();
      scanner.next();
      final double cellSize = scanner.nextDouble();
      scanner.next();
      final double noData = scanner.nextDouble();
      final double[][] values = new double[width][height];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          final double value = scanner.nextDouble();
          values[x][y] = value == noData ? Double.NaN : value;
        }
      }
      return new PipelineRaster(
        width,
        height,
        new Envelope(
          west,
          west + width * cellSize,
          south,
          south + height * cellSize
        ),
        values
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Table table(final String fileName) {
    return Table.read().csv(INPUT_PATH.resolve(fileName).toFile());
  }

  @BeforeEach
  void startSimulation() {
    final String yaml = new ScenarioWriter().write(
      new AnchovyBayScenario().get()
    );
    simulation = new ScenarioLoader("eu.project.surimi")
      .load(yaml)
      .startNewSimulation();
  }

  @AfterEach
  void finishSimulation() {
    simulation.finish();
  }

  @Test
  void startsAtTheStartDate() {
    assertThat(simulation.getTemporalSchedule().getDateTime()).isEqualTo(
      START_DATE.atStartOfDay()
    );
  }

  @Test
  void hasThePipelineGrid() {
    final ModelGrid modelGrid = simulation.getComponent(ModelGrid.class);
    final PipelineRaster bathymetry = raster("bathymetry_grid.asc");

    assertThat(modelGrid.getGridWidth()).isEqualTo(bathymetry.width());
    assertThat(modelGrid.getGridHeight()).isEqualTo(bathymetry.height());
    assertThat(modelGrid.getEnvelope()).isEqualTo(bathymetry.envelope());
  }

  @Test
  void hasThePipelineActiveCells() {
    final ModelGrid modelGrid = simulation.getComponent(ModelGrid.class);

    raster("exclusion_grid.asc").forEach((cell, value) ->
      assertThat(modelGrid.isActive(cell))
        .as("active at %s", cell)
        .isEqualTo(value == 0)
    );
  }

  @Test
  void hasThePipelineBathymetry() {
    final BathymetricGrid bathymetry = simulation.getComponent(
      BathymetricGrid.class
    );

    raster("bathymetry_grid.asc").forEach((cell, value) ->
      assertThat(bathymetry.getElevation(cell))
        .as("elevation at %s", cell)
        .isEqualTo(value)
    );
  }

  @Test
  void hasEveryPipelineSpecies() {
    final Table species = table("species.csv");

    assertThat(simulation.getComponents(Species.class))
      .extracting(Species::getCode, Species::getName, Species::getLifeStage)
      .containsExactlyInAnyOrderElementsOf(
        species
          .stream()
          .map(row ->
            tuple(
              row.getString("species_code"),
              row.getString("species_name"),
              row.getString("life_stage").isBlank()
                ? null
                : row.getString("life_stage")
            )
          )
          .toList()
      );
  }

  @Test
  void hasEveryPipelinePortAtItsCoordinates() {
    final PortGrid portGrid = simulation.getComponent(PortGrid.class);
    final ModelGrid modelGrid = simulation.getComponent(ModelGrid.class);
    final Table ports = table("ports.csv");

    assertThat(portGrid.getPorts())
      .extracting(Port::getCode, Port::getName, portGrid::getCell)
      .containsExactlyInAnyOrderElementsOf(
        ports
          .stream()
          .map(row ->
            tuple(
              row.getString("port_code"),
              row.getString("port_name"),
              modelGrid.toCell(
                new Coordinate(row.getDouble("lon"), row.getDouble("lat"))
              )
            )
          )
          .toList()
      );
  }

  @Test
  void hasOneUnpricedMarketPerPort() {
    final PortGrid portGrid = simulation.getComponent(PortGrid.class);
    final MarketGrid marketGrid = simulation.getComponent(MarketGrid.class);

    assertThat(marketGrid.stream())
      .extracting(Market::getPort)
      .containsExactlyInAnyOrderElementsOf(portGrid.getPorts().toList());
    portGrid.getPorts().forEach(port -> {
      final Market market = marketGrid.getMarket(port);
      assertThat(market.getCode()).isEqualTo(port.getCode());
      assertThat(marketGrid.getCell(market)).isEqualTo(portGrid.getCell(port));
      assertThat(market).isInstanceOfSatisfying(
        BiomassMarket.class,
        biomassMarket -> {
          assertThat(biomassMarket.getCurrencyUnit()).isEqualTo(
            CurrencyUnit.EUR
          );
          assertThat(biomassMarket.getPrices()).isEmpty();
        }
      );
    });
  }

  @Test
  void startsWithZeroBiomassOnlyOnActiveWaterCellsForEverySpecies() {
    final ModelGrid modelGrid = simulation.getComponent(ModelGrid.class);
    final BathymetricGrid bathymetry = simulation.getComponent(
      BathymetricGrid.class
    );
    final var biomassGrids = simulation.getComponents(BiomassGrid.class);

    assertThat(biomassGrids)
      .extracting(BiomassGrid::getSpecies)
      .containsExactlyInAnyOrderElementsOf(
        simulation.getComponents(Species.class)
      );
    assertThat(biomassGrids).allSatisfy(grid ->
      modelGrid.getAllCells().forEach(cell -> {
        if (bathymetry.isActiveWater(cell)) {
          assertThat(grid.getValue(cell))
            .as("%s at %s", grid.getSpecies(), cell)
            .isZero();
        } else {
          assertThat(grid.getValue(cell))
            .as("%s at %s", grid.getSpecies(), cell)
            .isNaN();
        }
      })
    );
  }
}
