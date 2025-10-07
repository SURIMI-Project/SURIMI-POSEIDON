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

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import uk.ac.ox.poseidon.biology.species.Species;

import java.util.Optional;

import static java.util.function.Predicate.not;

@Value
@AllArgsConstructor
public class SpeciesKey {

    @NonNull String code;
    String lifeStage;

    public SpeciesKey(final @NonNull String code) {
        this.code = code;
        this.lifeStage = null;
    }

    public Species toSpecies() {
        return new Species(code, lifeStage);
    }

    public static SpeciesKey from(final Species species) {
        return new SpeciesKey(species.getCode(), species.getLifeStage());
    }

    public static SpeciesKey from(final build.buf.gen.surimi.v1.Species species) {
        return new SpeciesKey(
            species.getSpeciesCode(),
            Optional
                .of(species.getStage())
                .filter(not(String::isBlank))
                .orElse(null)
        );
    }
}
