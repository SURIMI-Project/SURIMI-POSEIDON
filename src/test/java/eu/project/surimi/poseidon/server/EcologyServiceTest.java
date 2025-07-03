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

import build.buf.gen.surimi.v1.*;
import eu.project.surimi.poseidon.scenarios.MinimalScenario;
import org.junit.jupiter.api.Test;
import tech.units.indriya.format.SimpleUnitFormat;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Unit;
import javax.measure.quantity.Mass;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static eu.project.surimi.poseidon.scenarios.MinimalScenario.CARRYING_CAPACITY;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections.CollectionUtils.isEqualCollection;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EcologyServiceTest extends ServiceTest {

    @Test
    void canGetBiomass() {
        final String simulationId = UUID.randomUUID().toString();
        initialiseSimulation(simulationId);
        final GetBiomassResponse response = ecologyStub.getBiomass(
            GetBiomassRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .build()
        );
        final BiomassSummary biomassSummary = response.getBiomassSummary();
        final Unit<Mass> unit =
            SimpleUnitFormat
                .getInstance()
                .parse(biomassSummary.getMeasurementUnit())
                .asType(Mass.class);
        final Map<String, List<BiomassCell>> grids = biomassSummary
            .getBiomassGridsList()
            .stream()
            .collect(toMap(
                BiomassGrid::getSpeciesCode,
                BiomassGrid::getBiomassCellsList
            ));
        assertTrue(isEqualCollection(MinimalScenario.SPECIES_CODES, grids.keySet()));
        grids.values().forEach(biomassCells ->
            biomassCells.forEach(biomassCell -> {
                assertTrue(
                    Quantities.getQuantity(biomassCell.getBiomass(), unit)
                        .isEquivalentTo(CARRYING_CAPACITY)
                );
            }));
    }
}
