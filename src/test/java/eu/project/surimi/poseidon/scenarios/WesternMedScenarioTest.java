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

import build.buf.gen.surimi.v1.FinaliseRequest;
import build.buf.gen.surimi.v1.SimulateStepRequest;
import eu.project.surimi.poseidon.server.ServiceTest;
import org.junit.jupiter.api.Test;

import static java.util.stream.IntStream.range;

class WesternMedScenarioTest extends ServiceTest {

    public WesternMedScenarioTest() {
        super(WesternMedScenario.class);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void canRunForAYear() {
        final int numYears = 1;
        final String simulationId = initialiseSimulation();
        final SimulateStepRequest simulateStepRequest = SimulateStepRequest
            .newBuilder()
            .setSimulationId(simulationId)
            .build();
        range(0, numYears * 12).forEach(i ->
            workflowStub.simulateStep(simulateStepRequest)
        );
        workflowStub.finalise(FinaliseRequest.newBuilder().setSimulationId(simulationId).build());
    }
}
