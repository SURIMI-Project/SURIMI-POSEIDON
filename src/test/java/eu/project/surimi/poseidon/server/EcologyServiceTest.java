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
import tech.units.indriya.ComparableQuantity;
import uk.ac.ox.poseidon.geography.Coordinate;

import javax.measure.Unit;
import javax.measure.quantity.Mass;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

import static eu.project.surimi.poseidon.scenarios.MinimalScenario.CARRYING_CAPACITY;
import static eu.project.surimi.poseidon.scenarios.MinimalScenario.LIFE_STAGE_PER_SPECIES_CODE;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections.CollectionUtils.isEqualCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.units.indriya.quantity.Quantities.getQuantity;
import static tech.units.indriya.unit.Units.GRAM;
import static uk.ac.ox.poseidon.core.utils.Measurements.parseMassUnit;

public class EcologyServiceTest extends ServiceTest {

    public EcologyServiceTest() {
        super(MinimalScenario.class);
    }

    @Test
    void canGetBiomass() {
        final String simulationId = initialiseSimulation();
        final Map<Species, Map<Coordinate, ComparableQuantity<Mass>>> grids =
            getGrids(simulationId);
        assertTrue(
            isEqualCollection(
                LIFE_STAGE_PER_SPECIES_CODE.stream().map(Map.Entry::getKey).toList(),
                grids.keySet().stream().map(Species::getSpeciesCode).toList()
            )
        );
        assertTrue(
            isEqualCollection(
                LIFE_STAGE_PER_SPECIES_CODE.stream().map(Map.Entry::getValue).toList(),
                grids.keySet().stream().map(species ->
                    Optional.of(species.getStage()).filter(s -> !s.isEmpty()).orElse(null)
                ).toList()
            )
        );
        assertTrue(
            grids.values().stream().allMatch(grid ->
                grid.values().stream().allMatch(biomass ->
                    biomass.isEquivalentTo(CARRYING_CAPACITY)
                )
            )
        );
    }

    private Map<Species, Map<Coordinate, ComparableQuantity<Mass>>> getGrids(final String simulationId) {
        final GetBiomassResponse response = ecologyStub.getBiomass(
            GetBiomassRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .build()
        );
        return readBiomassGrids(response.getBiomassSummary());
    }

    Map<Species, Map<Coordinate, ComparableQuantity<Mass>>> readBiomassGrids(
        final BiomassSummary biomassSummary
    ) {
        final Unit<Mass> unit =
            parseMassUnit(biomassSummary.getMeasurementUnit());
        return biomassSummary
            .getBiomassGridsList()
            .stream()
            .collect(toMap(
                BiomassGrid::getSpecies,
                biomassGrid ->
                    biomassGrid
                        .getBiomassCellsList()
                        .stream()
                        .collect(toMap(
                            cell -> new Coordinate(cell.getLongitude(), cell.getLatitude()),
                            cell -> getQuantity(cell.getBiomass(), unit)
                        ))
            ));
    }

    @Test
    void updateBiomass() {
        final Unit<Mass> unit = GRAM;
        final String simulationId = initialiseSimulation();
        final UpdateBiomassResponse updateBiomassResponse =
            ecologyStub.updateBiomass(
                UpdateBiomassRequest
                    .newBuilder()
                    .setSimulationId(simulationId)
                    .setBiomassSummary(
                        BiomassSummary
                            .newBuilder()
                            .setMeasurementUnit(unit.toString())
                            .addBiomassGrids(
                                BiomassGrid
                                    .newBuilder()
                                    .setSpecies(
                                        Species
                                            .newBuilder()
                                            .setSpeciesCode(
                                                LIFE_STAGE_PER_SPECIES_CODE.getFirst().getKey()
                                            ).setStage(
                                                LIFE_STAGE_PER_SPECIES_CODE.getFirst().getValue()
                                            ))
                                    .addBiomassCells(
                                        BiomassCell
                                            .newBuilder()
                                            .setLongitude(-1)
                                            .setLatitude(-1)
                                            .setBiomass(10)
                                    )
                                    .addBiomassCells(
                                        BiomassCell
                                            .newBuilder()
                                            .setLongitude(0)
                                            .setLatitude(-1)
                                            .setBiomass(20)
                                    )
                            )
                            .addBiomassGrids(
                                BiomassGrid
                                    .newBuilder()
                                    .setSpecies(
                                        Species
                                            .newBuilder()
                                            .setSpeciesCode(
                                                LIFE_STAGE_PER_SPECIES_CODE.getLast().getKey()
                                            )
                                    )
                                    .addBiomassCells(
                                        BiomassCell
                                            .newBuilder()
                                            .setLongitude(-1)
                                            .setLatitude(-1)
                                            .setBiomass(30)
                                    )
                            )
                    )
                    .build()
            );
        assertEquals(simulationId, updateBiomassResponse.getSimulationId());
        final Map<Map.Entry<String, String>, Map<Coordinate, ComparableQuantity<Mass>>> grids =
            getGrids(simulationId)
                .entrySet()
                .stream()
                .collect(
                    toMap(
                        entry -> new AbstractMap.SimpleEntry<>(
                            entry.getKey().getSpeciesCode(),
                            Optional
                                .of(entry.getKey().getStage())
                                .filter(s -> !s.isEmpty())
                                .orElse(null)
                        ),
                        Map.Entry::getValue
                    )
                );
        assertTrue(
            grids.get(LIFE_STAGE_PER_SPECIES_CODE.getFirst())
                .get(new Coordinate(-1, -1))
                .isEquivalentTo(getQuantity(10, unit))
        );
        assertTrue(
            grids.get(LIFE_STAGE_PER_SPECIES_CODE.getFirst())
                .get(new Coordinate(0, -1))
                .isEquivalentTo(getQuantity(20, unit))
        );
        assertTrue(
            grids.get(LIFE_STAGE_PER_SPECIES_CODE.getLast())
                .get(new Coordinate(-1, -1))
                .isEquivalentTo(getQuantity(30, unit))
        );

    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void someBiomassGetsRemovedAfterAStep() {
        final String simulationId = initialiseSimulation();
        final var initialGrids = getGrids(simulationId);
        workflowStub.simulateStep(
            SimulateStepRequest
                .newBuilder()
                .setSimulationId(simulationId)
                .build()
        );
        final var updatedGrids = getGrids(simulationId);
        initialGrids.forEach((speciesCode, initialGrid) -> {
            final var updatedGrid = updatedGrids.get(speciesCode);
            // Check that no cell has seen an increase in biomass
            assertTrue(
                initialGrid.entrySet().stream().allMatch(entry ->
                    updatedGrid.get(entry.getKey()).isLessThanOrEqualTo(entry.getValue())
                )
            );
            // and that some biomass has been removed (i.e., fished) in at least one cell
            assertTrue(
                initialGrid.entrySet().stream().anyMatch(entry ->
                    updatedGrid.get(entry.getKey()).isLessThan(entry.getValue())
                )
            );
        });
    }
}
