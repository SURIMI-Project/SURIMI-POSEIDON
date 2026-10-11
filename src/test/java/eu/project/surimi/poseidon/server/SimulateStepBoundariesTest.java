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

import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import org.junit.jupiter.api.Test;
import sim.engine.Steppable;
import uk.ac.ox.poseidon.agents.market.BiomassSaleAccumulator;
import uk.ac.ox.poseidon.agents.market.Sale;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.events.EventAccumulator;
import uk.ac.ox.poseidon.core.schedule.TemporalSchedule;

import java.time.LocalDateTime;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps a served simulation month by month, as the controller does, across a leap February and a
 * year end, and checks that each step covers its whole month.
 */
public class SimulateStepBoundariesTest extends ServiceTest {

    private static final Period MONTH = Period.parse(STEP_SIZE);
    private static final int MONTHS = 14;

    public SimulateStepBoundariesTest() {
        super(MinimalScenario.class);
    }

    @Test
    void eachStepEndsJustBeforeTheControllersNextDateTime() {
        final String simulationId = initialiseSimulation();
        final TemporalSchedule schedule =
            simulationManager.getSimulation(simulationId).getTemporalSchedule();
        LocalDateTime controllerDateTime = START_DATE_TIME;
        for (int month = 1; month <= MONTHS; month++) {
            step(simulationId, controllerDateTime);
            controllerDateTime = controllerDateTime.plus(MONTH);
            assertThat(schedule.getDateTime())
                .as("simulation date-time after step %d", month)
                .isEqualTo(controllerDateTime.minusSeconds(1));
        }
    }

    @Test
    void monthlyReadsSeeEverySaleOnceWhenAccumulatorsAreClearedMonthly() {
        final String simulationId = initialiseSimulation();
        final Simulation simulation = simulationManager.getSimulation(simulationId);
        // What GetSales reports from, cleared at the start of each month as in the NW Med scenario
        final BiomassSaleAccumulator reportedSales =
            simulation.getComponent(BiomassSaleAccumulator.class);
        simulation.getTemporalSchedule().scheduleRepeating(
            START_DATE_TIME.plus(MONTH),
            -2,
            (Steppable) _ -> reportedSales.clear(),
            MONTH
        );
        final EventAccumulator<Sale> allSales = new EventAccumulator<>(Sale.class);
        simulation.getEventManager().addListener(allSales);

        // Counted rather than weighed: MinimalScenario's sales carry no biomass after January
        long salesRead = 0;
        LocalDateTime controllerDateTime = START_DATE_TIME;
        for (int month = 1; month <= MONTHS; month++) {
            step(simulationId, controllerDateTime);
            salesRead += reportedSales.getEvents().count();
            controllerDateTime = controllerDateTime.plus(MONTH);
        }

        assertThat(allSales.getEvents().count()).isPositive();
        assertThat(salesRead).isEqualTo(allSales.getEvents().count());
    }
}
