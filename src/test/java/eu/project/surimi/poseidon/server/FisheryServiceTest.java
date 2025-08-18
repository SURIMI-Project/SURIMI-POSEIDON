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

package eu.project.surimi.poseidon.server;

import build.buf.gen.surimi.v1.DispositionGrid;
import build.buf.gen.surimi.v1.GetCatchDispositionRequest;
import eu.project.surimi.poseidon.scenarios.MinimalScenario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static eu.project.surimi.poseidon.scenarios.MinimalScenario.GEAR_CODES;
import static eu.project.surimi.poseidon.scenarios.MinimalScenario.SPECIES_CODES;
import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FisheryServiceTest extends ServiceTest {
    public FisheryServiceTest() {
        super(MinimalScenario.class);
    }

    @Test
    void getCatchDisposition() {
        final String simulationId = initialiseSimulation();
        step(simulationId);
        final List<DispositionGrid> dispositionGrids =
            fisheryStub
                .getCatchDisposition(
                    GetCatchDispositionRequest
                        .newBuilder()
                        .setStartDateTime(
                            toTimestamp(MinimalScenario.START_DATE.atStartOfDay())
                        )
                        .setEndDateTime(
                            toTimestamp(MinimalScenario.START_DATE.plusMonths(1).atStartOfDay())
                        )
                        .setSimulationId(simulationId)
                        .build()
                )
                .getCatchDispositionSummary()
                .getDispositionGridsList();
        // Just check that we get a grid for each gear/species combination
        assertEquals(
            GEAR_CODES.stream().collect(toMap(
                identity(),
                gc -> Set.copyOf(SPECIES_CODES)
            )),
            dispositionGrids
                .stream()
                .collect(
                    groupingBy(
                        dispositionGrid -> dispositionGrid.getFleetSegment().getGearCode(),
                        mapping(
                            species -> species.getSpecies().getSpeciesCode(),
                            toSet()
                        )
                    )
                )
        );
    }
}
