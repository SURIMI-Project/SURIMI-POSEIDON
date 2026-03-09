package eu.project.surimi.poseidon.server.fleet;

import lombok.Value;

import java.util.Comparator;
import java.util.Objects;

/**
 * Domain fleet-segment descriptor with wildcard coverage semantics:
 * null fields broaden the segment and therefore match any corresponding value.
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
    public int compareTo(final FleetSegment other) {
        return Comparator
            .comparing(FleetSegment::getGearCode, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(FleetSegment::getVesselLengthClass, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(FleetSegment::getScale, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(FleetSegment::getCountryCode, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(FleetSegment::getModel, Comparator.nullsFirst(Comparator.naturalOrder()))
            .compare(this, other);
    }

    private static boolean covers(final Object expected, final Object actual) {
        return expected == null || Objects.equals(expected, actual);
    }

    private static boolean overlaps(final Object first, final Object second) {
        return first == null || second == null || Objects.equals(first, second);
    }
}
