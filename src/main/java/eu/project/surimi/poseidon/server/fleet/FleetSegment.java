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

import lombok.Value;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Objects;

/**
 * Domain fleet-segment descriptor with wildcard coverage semantics: null fields broaden the segment
 * and therefore match any corresponding value.
 */
@Value
public class FleetSegment implements Comparable<FleetSegment> {

    String gearCode;
    String vesselLengthClass;
    String scale;
    String countryCode;
    String model;

    /**
     * Returns true when this segment is at least as general as {@code other}.
     */
    public boolean covers(final FleetSegment other) {
        return covers(gearCode, other.gearCode) &&
            covers(vesselLengthClass, other.vesselLengthClass) &&
            covers(scale, other.scale) &&
            covers(countryCode, other.countryCode) &&
            covers(model, other.model);
    }

    /**
     * Returns true when this segment and {@code other} share at least one common concrete
     * fleet-segment combination under the null-as-wildcard semantics.
     */
    public boolean overlaps(final FleetSegment other) {
        return overlaps(gearCode, other.gearCode) &&
            overlaps(vesselLengthClass, other.vesselLengthClass) &&
            overlaps(scale, other.scale) &&
            overlaps(countryCode, other.countryCode) &&
            overlaps(model, other.model);
    }

    @Override
    public int compareTo(final @NonNull FleetSegment other) {
        return Comparator
            .comparing(FleetSegment::getGearCode, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(
                FleetSegment::getVesselLengthClass,
                Comparator.nullsFirst(Comparator.naturalOrder())
            )
            .thenComparing(FleetSegment::getScale, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(
                FleetSegment::getCountryCode,
                Comparator.nullsFirst(Comparator.naturalOrder())
            )
            .thenComparing(FleetSegment::getModel, Comparator.nullsFirst(Comparator.naturalOrder()))
            .compare(this, other);
    }

    private static boolean covers(
        final Object expected,
        final Object actual
    ) {
        return expected == null || Objects.equals(expected, actual);
    }

    private static boolean overlaps(
        final Object first,
        final Object second
    ) {
        return first == null || second == null || Objects.equals(first, second);
    }
}
