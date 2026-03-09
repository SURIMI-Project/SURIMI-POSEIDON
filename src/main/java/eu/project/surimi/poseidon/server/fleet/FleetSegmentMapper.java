package eu.project.surimi.poseidon.server.fleet;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.core.utils.NumericIntervalMapper;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Maps a {@link Vessel} to a {@link FleetSegment} object.
 *
 * <p>The mapper reads:
 *
 * <ul>
 * <li>gear code from {@code vessel.getGear().getCode()}</li>
 * <li>country code from a configured vessel tag</li>
 * <li>vessel length from a configured vessel tag, mapped to a vessel-length class
 * via a {@link NumericIntervalMapper}</li>
 * <li>scale and model from configured constants</li>
 * </ul>
 *
 * <p>Tag values are normalised before use. Blank strings and {@code NA} are treated
 * as missing. Numeric tag values may be provided either as {@link Number} instances
 * or as parseable strings.
 *
 * <p>Missing, invalid, or unclassified values are mapped to {@code null}. This
 * preserves the wildcard semantics used by {@link FleetSegment#covers(FleetSegment)}.
 */
@RequiredArgsConstructor
public class FleetSegmentMapper implements Function<Vessel, FleetSegment> {

    private final @NonNull String countryCodeTag;
    private final @NonNull String vesselLengthTag;
    private final @NonNull NumericIntervalMapper<String> vesselLengthClassMapper;
    private final String scale;
    private final String model;

    @Override
    public FleetSegment apply(final Vessel vessel) {
        final Vessel checkedVessel = checkNotNull(vessel);

        return new FleetSegment(
            getGearCode(checkedVessel),
            getVesselLengthClass(checkedVessel),
            scale,
            getStringTag(checkedVessel, countryCodeTag),
            model
        );
    }

    private String getGearCode(final Vessel vessel) {
        return vessel.getGear() == null ? null : vessel.getGear().getCode();
    }

    private String getVesselLengthClass(final Vessel vessel) {
        final Double vesselLength = getNumericTag(vessel, vesselLengthTag);
        if (vesselLength == null) {
            return null;
        }
        return vesselLengthClassMapper.get(vesselLength).orElse(null);
    }

    private String getStringTag(
        final Vessel vessel,
        final String tagName
    ) {
        return vessel.getTag(tagName)
            .map(FleetSegmentMapper::normaliseString)
            .orElse(null);
    }

    private Double getNumericTag(
        final Vessel vessel,
        final String tagName
    ) {
        return vessel.getTag(tagName)
            .map(FleetSegmentMapper::toDouble)
            .orElse(null);
    }

    private static String normaliseString(final Object value) {
        if (value == null) {
            return null;
        }
        final String stringValue = value.toString().trim();
        if (stringValue.isEmpty() || stringValue.equalsIgnoreCase("NA")) {
            return null;
        }
        return stringValue;
    }

    private static Double toDouble(final Object value) {
        if (value instanceof final Number number) {
            final double doubleValue = number.doubleValue();
            return Double.isFinite(doubleValue) ? doubleValue : null;
        }
        final String stringValue = normaliseString(value);
        if (stringValue == null) {
            return null;
        }
        try {
            return Double.valueOf(stringValue);
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }
}
