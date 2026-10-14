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

package eu.project.surimi.poseidon.server;

import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.withinPercentage;

import build.buf.gen.surimi.v1.BiomassCell;
import build.buf.gen.surimi.v1.BiomassGrid;
import build.buf.gen.surimi.v1.BiomassSummary;
import build.buf.gen.surimi.v1.DispositionCell;
import build.buf.gen.surimi.v1.GetCatchDispositionRequest;
import build.buf.gen.surimi.v1.GetSalesRequest;
import build.buf.gen.surimi.v1.UpdateBiomassRequest;
import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sim.engine.Steppable;
import uk.ac.ox.poseidon.agents.market.BiomassSaleAccumulator;
import uk.ac.ox.poseidon.agents.market.Sale;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEvent;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEventAccumulator;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.events.EventAccumulator;
import uk.ac.ox.poseidon.core.schedule.TemporalSchedule;
import uk.ac.ox.poseidon.geography.Coordinate;

/**
 * Steps a served simulation month by month, as the controller does, across a leap February and a
 * year end, and checks that each step covers its whole month, up to and including its last second,
 * and that the controller's reads after each step report everything that happened in it.
 */
public class SimulateStepBoundariesTest extends ServiceTest {

  private static final Period MONTH = Period.parse(STEP_SIZE);
  private static final int MONTHS = 14;

  public SimulateStepBoundariesTest() {
    super(MinimalScenario.class);
  }

  @Test
  void eachStepEndsAtTheControllersNextDateTime() {
    final String simulationId = initialiseSimulation();
    final TemporalSchedule schedule = simulationManager
      .getSimulation(simulationId)
      .getTemporalSchedule();
    LocalDateTime controllerDateTime = START_DATE_TIME;
    for (int month = 1; month <= MONTHS; month++) {
      step(simulationId, controllerDateTime);
      controllerDateTime = controllerDateTime.plus(MONTH);
      assertThat(schedule.getDateTime())
        .as("simulation date-time after step %d", month)
        .isEqualTo(controllerDateTime);
    }
  }

  @Test
  void monthlyReadsReportEverySaleAndCatchWhenAccumulatorsAreClearedMonthly() {
    final String simulationId = initialiseSimulation();
    final Simulation simulation = simulationManager.getSimulation(simulationId);
    // MinimalScenario's vessels empty its biomass within a month: restore it at each step, as
    // the controller's biomass updates would, so that every month has catches and sales.
    final BiomassSummary initialBiomass = biomassSummary(simulation);
    // What GetSales and GetCatchDisposition report from, cleared at the start of each month
    // as in the NW Med scenario
    final BiomassSaleAccumulator reportedSales = simulation.getComponent(
      BiomassSaleAccumulator.class
    );
    final FishingEventAccumulator reportedCatches = simulation.getComponent(
      FishingEventAccumulator.class
    );
    simulation.getTemporalSchedule().scheduleRepeating(
      START_DATE_TIME.plus(MONTH),
      -2,
      (Steppable) _ -> {
        reportedSales.clear();
        reportedCatches.clear();
      },
      MONTH
    );
    final EventAccumulator<Sale> allSales = new EventAccumulator<>(Sale.class);
    final EventAccumulator<FishingEvent> allCatches = new EventAccumulator<>(
      FishingEvent.class
    );
    simulation.getEventManager().addListener(allSales);
    simulation.getEventManager().addListener(allCatches);

    // A sale in the last second of each month: a step that ended a second short, or left
    // that second to the next step (where the monthly clearing discards it before the
    // reads), would lose it. It copies the latest sale with a positive weight, so that its
    // loss shows in the reported weight.
    final Map<Integer, Double> lastSecondSaleKgByMonth = new HashMap<>();
    double salesReadInKg = 0;
    double grossCatchReadInKg = 0;
    LocalDateTime controllerDateTime = START_DATE_TIME;
    for (int month = 1; month <= MONTHS; month++) {
      final LocalDateTime nextControllerDateTime = controllerDateTime.plus(
        MONTH
      );
      final int thisMonth = month;
      simulation.getTemporalSchedule().scheduleOnce(
        nextControllerDateTime.minusSeconds(1),
        (Steppable) _ -> {
          final Sale latest = allSales
            .getEvents()
            .filter(sale -> kg(sale) > 0)
            .reduce((first, second) -> second)
            .orElseThrow(() ->
              new AssertionError("No sale with a positive weight")
            );
          lastSecondSaleKgByMonth.put(thisMonth, kg(latest));
          simulation
            .getEventManager()
            .broadcast(
              new Sale(
                simulation.getTemporalSchedule().getDateTime(),
                "last second of month " + thisMonth,
                latest.getMarket(),
                latest.getVessel(),
                latest.getItems(),
                latest.getUnsold()
              )
            );
        }
      );
      updateBiomass(simulationId, controllerDateTime, initialBiomass);
      step(simulationId, controllerDateTime);
      final double monthSalesReadInKg = getSalesInKg(
        simulationId,
        controllerDateTime,
        nextControllerDateTime
      );
      final LocalDateTime monthStart = controllerDateTime;
      final double monthSoldKg = allSales
        .getEvents()
        .filter(sale -> !sale.getDateTime().isBefore(monthStart))
        .filter(sale -> sale.getDateTime().isBefore(nextControllerDateTime))
        .mapToDouble(SimulateStepBoundariesTest::kg)
        .sum();
      assertThat(lastSecondSaleKgByMonth.get(month))
        .as("weight of the sale in the last second of month %d", month)
        .isPositive();
      assertThat(monthSalesReadInKg)
        .as("sales read for month %d", month)
        .isCloseTo(monthSoldKg, withinPercentage(1e-6));
      salesReadInKg += monthSalesReadInKg;
      grossCatchReadInKg += getGrossCatchInKg(
        simulationId,
        controllerDateTime,
        nextControllerDateTime
      );
      controllerDateTime = nextControllerDateTime;
    }

    final double soldKg = allSales
      .getEvents()
      .mapToDouble(SimulateStepBoundariesTest::kg)
      .sum();
    final double caughtKg = allCatches
      .getEvents()
      .mapToDouble(event ->
        event.getOutcome().getGrossCatch().getTotalBiomassInKg()
      )
      .sum();
    assertThat(soldKg).isPositive();
    assertThat(caughtKg).isPositive();
    assertThat(salesReadInKg).isCloseTo(soldKg, withinPercentage(1e-6));
    assertThat(grossCatchReadInKg).isCloseTo(caughtKg, withinPercentage(1e-6));
  }

  @Test
  void acceptsRequestsAtTheSimulationDateTime() {
    final String simulationId = initialiseSimulation();
    assertThatCode(() ->
      step(simulationId, START_DATE_TIME)
    ).doesNotThrowAnyException();
    assertThatCode(() ->
      step(simulationId, START_DATE_TIME.plus(MONTH))
    ).doesNotThrowAnyException();
  }

  @Test
  void rejectsRequestsAfterTheSimulationDateTime() {
    final String simulationId = initialiseSimulation();
    assertRejected(() -> step(simulationId, START_DATE_TIME.plusSeconds(1)));
    assertRejected(() -> step(simulationId, START_DATE_TIME.plus(MONTH)));
  }

  @Test
  void rejectsRequestsBeforeTheSimulationDateTime() {
    final String simulationId = initialiseSimulation();
    assertRejected(() -> step(simulationId, START_DATE_TIME.minusSeconds(1)));
    step(simulationId, START_DATE_TIME);
    assertRejected(() -> step(simulationId, START_DATE_TIME));
  }

  private static void assertRejected(final Runnable request) {
    assertThatThrownBy(request::run).isInstanceOfSatisfying(
      StatusRuntimeException.class,
      e ->
        assertThat(e.getStatus().getCode()).isEqualTo(
          Status.Code.INVALID_ARGUMENT
        )
    );
  }

  private static double kg(final Sale sale) {
    return sale
      .getItems()
      .stream()
      .mapToDouble(item -> item.getContent().asBiomass().asKg())
      .sum();
  }

  private static BiomassSummary biomassSummary(final Simulation simulation) {
    final BiomassSummary.Builder summary = BiomassSummary.newBuilder();
    simulation
      .getComponents(uk.ac.ox.poseidon.biology.biomass.BiomassGrid.class)
      .forEach(grid -> {
        final BiomassGrid.Builder protoGrid =
          BiomassGrid.newBuilder().setSpecies(
            new SpeciesKey(
              grid.getSpecies().getCode(),
              grid.getSpecies().getLifeStage()
            ).toProtobufSpecies()
          );
        grid
          .getModelGrid()
          .getActiveCells()
          .stream()
          .filter(cell -> Double.isFinite(grid.getValue(cell)))
          .forEach(cell -> {
            final Coordinate coordinate = grid
              .getModelGrid()
              .toCoordinate(cell);
            protoGrid.addBiomassCells(
              BiomassCell.newBuilder()
                .setLongitude(coordinate.lon)
                .setLatitude(coordinate.lat)
                .setBiomass(grid.getValue(cell))
            );
          });
        summary.addBiomassGrids(protoGrid);
      });
    return summary.build();
  }

  private void updateBiomass(
    final String simulationId,
    final LocalDateTime dateTime,
    final BiomassSummary biomass
  ) {
    ecologyConsumerStub.updateBiomass(
      UpdateBiomassRequest.newBuilder()
        .setSimulationId(simulationId)
        .setDateTime(toTimestamp(dateTime))
        .setBiomassSummary(biomass)
        .build()
    );
  }

  private double getSalesInKg(
    final String simulationId,
    final LocalDateTime startDateTime,
    final LocalDateTime endDateTime
  ) {
    return salesProviderStub
      .getSales(
        GetSalesRequest.newBuilder()
          .setSimulationId(simulationId)
          .setStartDateTime(toTimestamp(startDateTime))
          .setEndDateTime(toTimestamp(endDateTime))
          .build()
      )
      .getSalesSummary()
      .getMarketSalesList()
      .stream()
      .flatMap(marketSales -> marketSales.getSalesList().stream())
      .mapToDouble(build.buf.gen.surimi.v1.Sale::getQuantity)
      .sum();
  }

  private double getGrossCatchInKg(
    final String simulationId,
    final LocalDateTime startDateTime,
    final LocalDateTime endDateTime
  ) {
    return catchProviderStub
      .getCatchDisposition(
        GetCatchDispositionRequest.newBuilder()
          .setSimulationId(simulationId)
          .setStartDateTime(toTimestamp(startDateTime))
          .setEndDateTime(toTimestamp(endDateTime))
          .build()
      )
      .getCatchDispositionSummary()
      .getDispositionGridsList()
      .stream()
      .flatMap(grid -> grid.getDispositionCellsList().stream())
      .mapToDouble(DispositionCell::getGrossCatch)
      .sum();
  }
}
