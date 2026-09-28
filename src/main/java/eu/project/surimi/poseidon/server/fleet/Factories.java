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

package eu.project.surimi.poseidon.server.fleet;

import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.functions.NumericIntervalMapper;
import uk.ac.ox.poseidon.core.scopes.Scope;

/**
 * Static entry points for building this package's factories, mirroring the YAML factory-method
 * convention used throughout POSEIDON scenarios.
 */
public class Factories {

    private Factories() {
    }

    /**
     * @return a {@link FleetSegmentMapperFactory} using the given tags and length classifier.
     */
    public static <S extends Scope> FleetSegmentMapperFactory<S> fleetSegmentMapper(
        final String countryCodeTag,
        final String vesselLengthTag,
        final Factory<? super S, ? extends NumericIntervalMapper<String>> vesselLengthClassMapper,
        final String scale,
        final String model
    ) {
        return new FleetSegmentMapperFactory<>(
            countryCodeTag,
            vesselLengthTag,
            vesselLengthClassMapper,
            scale,
            model
        );
    }
}
