package eu.project.surimi.poseidon.server.fleet;

import lombok.Value;

import java.util.Objects;

/**
 * Domain fleet-segment descriptor with wildcard coverage semantics:
 * null fields broaden the segment and therefore match any corresponding value.
 */
@Value
public class FleetSegment {

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

    private static boolean covers(final Object expected, final Object actual) {
        return expected == null || Objects.equals(expected, actual);
    }
}
