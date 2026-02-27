/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025-2026, University of Oxford.
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

package eu.project.surimi.poseidon.server.ecology;

import build.buf.gen.surimi.v1.*;
import eu.project.surimi.poseidon.scenarios.MinimalScenario;
import eu.project.surimi.poseidon.server.ServiceTest;
import eu.project.surimi.poseidon.server.SpeciesKey;
import org.junit.jupiter.api.Test;
import tech.units.indriya.ComparableQuantity;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.utils.Pair;
import uk.ac.ox.poseidon.geography.Coordinate;

import javax.measure.Unit;
import javax.measure.quantity.Mass;
import java.util.Map;
import java.util.Map.Entry;

import static eu.project.surimi.poseidon.scenarios.MinimalScenario.LIFE_STAGE_PER_SPECIES_CODE;
import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.units.indriya.quantity.Quantities.getQuantity;
import static tech.units.indriya.unit.Units.GRAM;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class EcologyConsumerServiceTest extends ServiceTest {

    public EcologyConsumerServiceTest() {
        super(MinimalScenario.class);
    }

    private Map<Species, Map<Coordinate, ComparableQuantity<Mass>>> getGrids(
        final String simulationId
    ) {
        final Simulation simulation = simulationManager.getSimulation(simulationId);
        return simulation
            .getComponents(uk.ac.ox.poseidon.biology.biomass.BiomassGrid.class)
            .stream()
            .collect(toMap(
                grid -> new SpeciesKey(
                    grid.getSpecies().getCode(),
                    grid.getSpecies().getLifeStage()
                ).toProtobufSpecies(),
                grid ->
                    grid
                        .getModelGrid()
                        .getActiveCells()
                        .stream()
                        .filter(cell -> Double.isFinite(grid.getValue(cell)))
                        .collect(toMap(
                            grid.getModelGrid()::toCoordinate,
                            cell -> getQuantity(grid.getValue(cell), KILOGRAM)
                        ))
            ));
    }

    @Test
    void updateBiomass() {
        final Unit<Mass> unit = GRAM;
        final String simulationId = initialiseSimulation();
        final UpdateBiomassResponse updateBiomassResponse =
            ecologyConsumerStub.updateBiomass(
                UpdateBiomassRequest
                    .newBuilder()
                    .setSimulationId(simulationId)
                    .setDateTime(toTimestamp(START_DATE_TIME))
                    .setBiomassSummary(
                        BiomassSummary
                            .newBuilder()
                            .addBiomassGrids(
                                BiomassGrid
                                    .newBuilder()
                                    .setSpecies(
                                        Species
                                            .newBuilder()
                                            .setSpeciesCode(
                                                LIFE_STAGE_PER_SPECIES_CODE.getFirst().getFirst()
                                            ).setLifeStage(
                                                LIFE_STAGE_PER_SPECIES_CODE.getFirst().getSecond()
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
                                                LIFE_STAGE_PER_SPECIES_CODE.getLast().getFirst()
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
        final Map<Pair<String, String>, Map<Coordinate, ComparableQuantity<Mass>>> grids =
            getGrids(simulationId)
                .entrySet()
                .stream()
                .collect(
                    toMap(
                        entry ->
                            new SpeciesKey(
                                entry.getKey().getSpeciesCode(),
                                entry.getKey().getLifeStage()
                            ).toPair(),
                        Entry::getValue
                    )
                );
        assertTrue(
            grids.get(LIFE_STAGE_PER_SPECIES_CODE.getFirst())
                .get(new Coordinate(-1, -1))
                .isEquivalentTo(getQuantity(10_000, unit))
        );
        assertTrue(
            grids.get(LIFE_STAGE_PER_SPECIES_CODE.getFirst())
                .get(new Coordinate(0, -1))
                .isEquivalentTo(getQuantity(20_000, unit))
        );
        assertTrue(
            grids.get(LIFE_STAGE_PER_SPECIES_CODE.getLast())
                .get(new Coordinate(-1, -1))
                .isEquivalentTo(getQuantity(30_000, unit))
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
