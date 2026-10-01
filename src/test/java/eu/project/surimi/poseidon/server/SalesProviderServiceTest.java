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

import build.buf.gen.surimi.v1.FleetSegment;
import build.buf.gen.surimi.v1.GetSalesRequest;
import build.buf.gen.surimi.v1.Sale;
import eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static eu.project.surimi.poseidon.scenarios.minimal.MinimalScenario.RETAINED_SPECIES_CODES;
import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SalesProviderServiceTest extends ServiceTest {

    public SalesProviderServiceTest() {
        super(MinimalScenario.class);
    }

    @Test
    void getSales() {
        final String simulationId = initialiseSimulation();
        step(simulationId, START_DATE_TIME);
        // Just check that we get sales for each contract fleet segment/species combination
        assertEquals(
            contractItems().getFleetSegmentsList().stream().collect(toMap(
                identity(),
                _ -> Set.copyOf(RETAINED_SPECIES_CODES)
            )),
            getSales(simulationId)
                .stream()
                .collect(
                    groupingBy(
                        Sale::getFleetSegment,
                        mapping(
                            sale -> sale.getSpecies().getSpeciesCode(),
                            toSet()
                        )
                    )
                )
        );
    }

    @Test
    void getSalesOnlyReportsFleetSegmentsInContract() {
        final FleetSegment g1 =
            FleetSegment.newBuilder().setGearCode("G1").setModel(Server.MODEL_NAME).build();
        final String simulationId = UUID.randomUUID().toString();
        initialiseSimulation(
            simulationId,
            MinimalScenario.class.getSimpleName(),
            contractItems().clearFleetSegments().addFleetSegments(g1).build()
        );
        step(simulationId, START_DATE_TIME);
        final Set<FleetSegment> reportedFleetSegments =
            getSales(simulationId)
                .stream()
                .map(Sale::getFleetSegment)
                .collect(toSet());
        assertEquals(Set.of(g1), reportedFleetSegments);
    }

    private List<Sale> getSales(final String simulationId) {
        return salesProviderStub
            .getSales(
                GetSalesRequest
                    .newBuilder()
                    .setSimulationId(simulationId)
                    .setStartDateTime(
                        toTimestamp(MinimalScenario.START_DATE.atStartOfDay())
                    )
                    .setEndDateTime(
                        toTimestamp(MinimalScenario.START_DATE.plusMonths(1).atStartOfDay())
                    )
                    .build()
            )
            .getSalesSummary()
            .getMarketSalesList()
            .stream()
            .flatMap(marketSales -> marketSales.getSalesList().stream())
            .toList();
    }
}
