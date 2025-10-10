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
import eu.project.surimi.poseidon.scenarios.WesternMedScenario;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class WesternMedBiomassGridsTest extends ServiceTest {

    public WesternMedBiomassGridsTest() {
        super(WesternMedScenario.class);
    }

    @Test
    void getBiomassGrids() {
        final String simulationId = initialiseSimulation();
        final double lon = -0.8102499656385;
        final double lat = 37.5553507693095;
        // noinspection ResultOfMethodCallIgnored
        ecologyStub.updateBiomass(
            UpdateBiomassRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .setBiomassSummary(
                    BiomassSummary
                        .newBuilder()
                        .setMeasurementUnit(KILOGRAM.toString())
                        .addBiomassGrids(
                            BiomassGrid
                                .newBuilder()
                                .setSpecies(
                                    Species
                                        .newBuilder()
                                        .setSpeciesCode("MTS")
                                )
                                .addBiomassCells(
                                    BiomassCell
                                        .newBuilder()
                                        .setLongitude(lon)
                                        .setLatitude(lat)
                                        .setBiomass(1)
                                )
                        )
                )
                .build()
        );
        final GetBiomassResponse response = ecologyStub.getBiomass(
            GetBiomassRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .build()
        );
        final BiomassSummary biomassSummary = response.getBiomassSummary();
        assertEquals(
            OptionalDouble.of(1),
            biomassSummary
                .getBiomassGridsList()
                .stream()
                .filter(grid -> grid.getSpecies().getSpeciesCode().equals("MTS"))
                .flatMap(grid -> grid.getBiomassCellsList().stream())
                .filter(cell -> cell.getLongitude() == lon && cell.getLatitude() == lat)
                .mapToDouble(BiomassCell::getBiomass)
                .findAny()
        );
    }
}
