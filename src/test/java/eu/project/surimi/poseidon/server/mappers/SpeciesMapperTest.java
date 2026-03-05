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

package eu.project.surimi.poseidon.server.mappers;

import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.biology.species.Species;

import static org.assertj.core.api.Assertions.assertThat;
import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toPoseidonSpecies;
import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toProtoSpecies;

class SpeciesMapperTest {

    @Test
    void toPoseidonSpeciesMapsCodeAndLifeStage() {
        final build.buf.gen.surimi.v1.Species protoSpecies =
            build.buf.gen.surimi.v1.Species
                .newBuilder()
                .setSpeciesCode("HKE")
                .setLifeStage("JUV")
                .build();

        final Species poseidonSpecies = toPoseidonSpecies(protoSpecies);

        assertThat(poseidonSpecies.getCode()).isEqualTo("HKE");
        assertThat(poseidonSpecies.getLifeStage()).isEqualTo("JUV");
    }

    @Test
    void toProtoSpeciesMapsCodeAndLifeStage() {
        final Species poseidonSpecies = new Species("HKE", "JUV", null);

        final build.buf.gen.surimi.v1.Species protoSpecies =
            toProtoSpecies(poseidonSpecies);

        assertThat(protoSpecies.getSpeciesCode()).isEqualTo("HKE");
        assertThat(protoSpecies.getLifeStage()).isEqualTo("JUV");
    }

    @Test
    void toProtoSpeciesOmitsBlankLifeStage() {
        final Species poseidonSpecies = new Species("HKE", "", null);

        final build.buf.gen.surimi.v1.Species protoSpecies =
            toProtoSpecies(poseidonSpecies);

        assertThat(protoSpecies.getSpeciesCode()).isEqualTo("HKE");
        assertThat(protoSpecies.getLifeStage()).isEmpty();
    }
}
