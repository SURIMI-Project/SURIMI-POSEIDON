package eu.project.surimi.poseidon.server.fleet;

import lombok.NonNull;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.extractors.tags.DoubleTagExtractor;
import uk.ac.ox.poseidon.agents.vessels.extractors.tags.StringTagExtractor;
import uk.ac.ox.poseidon.core.functions.NumericIntervalMapper;

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
public final class FleetSegmentMapper implements Function<Vessel, FleetSegment> {

    private final @NonNull StringTagExtractor countryCodeExtractor;
    private final @NonNull DoubleTagExtractor vesselLengthExtractor;
    private final @NonNull NumericIntervalMapper<String> vesselLengthClassMapper;
    private final String scale;
    private final String model;

    public FleetSegmentMapper(
        @NonNull final String countryCodeTag,
        @NonNull final String vesselLengthTag,
        @NonNull final NumericIntervalMapper<String> vesselLengthClassMapper,
        final String scale,
        final String model
    ) {
        this.countryCodeExtractor = new StringTagExtractor(countryCodeTag);
        this.vesselLengthExtractor = new DoubleTagExtractor(vesselLengthTag);
        this.vesselLengthClassMapper = vesselLengthClassMapper;
        this.scale = scale;
        this.model = model;
    }

    @Override
    public FleetSegment apply(final Vessel vessel) {
        checkNotNull(vessel);

        return new FleetSegment(
            getGearCode(vessel),
            getVesselLengthClass(vessel),
            scale,
            countryCodeExtractor.apply(vessel),
            model
        );
    }

    private String getGearCode(final Vessel vessel) {
        return vessel.getGear() == null ? null : vessel.getGear().getCode();
    }

    private String getVesselLengthClass(final Vessel vessel) {
        final Double vesselLength = vesselLengthExtractor.apply(vessel);
        if (vesselLength == null) {
            return null;
        }
        return vesselLengthClassMapper.apply(vesselLength);
    }

}
