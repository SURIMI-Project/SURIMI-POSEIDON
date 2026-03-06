package eu.project.surimi.poseidon.server.fleet;

import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.core.utils.NumericIntervalMapper;

/**
 * Maps a {@link Vessel} to the server-side {@link FleetSegment} domain object.
 *
 * <p>This class is intentionally left unimplemented for now. It exists as the
 * agreed design anchor for the next implementation session.
 *
 * <p>What this mapper should do:
 *
 * <ul>
 * <li>Read fleet-segment inputs from a vessel using both first-class vessel state
 * and vessel tags.</li>
 * <li>Produce the server/domain {@link FleetSegment} type, not the generated protobuf
 * message of the same conceptual name.</li>
 * <li>Build the result dynamically for a given vessel rather than storing a fleet
 * segment directly on {@code Vessel}.</li>
 * <li>Use a {@code NumericIntervalMapper<String>} to convert the vessel length
 * (expected to come from a numeric tag such as {@code loa}) into a DCF vessel
 * length class code such as {@code VL1218} or {@code VL1824}.</li>
 * <li>Populate values that come from scenario configuration rather than the vessel
 * itself, notably:
 * {@code scale} (expected to default to something like {@code "Industrial"} but stay configurable),
 * and likely {@code model} (for example {@code "POSEIDON"}).</li>
 * <li>Allow missing data to flow through as {@code null} where appropriate so that
 * {@link FleetSegment#covers(FleetSegment)} can use wildcard semantics. This is
 * important because quotas may be defined for broader fleet segments than the
 * vessel-specific segment, for example omitting vessel length class.</li>
 * </ul>
 *
 * <p>Configuration expected to come from a corresponding
 * {@code FleetSegmentMapperFactory}:
 *
 * <ul>
 * <li>Gear code should come from the vessel's actual gear via {@code vessel.getGear().getCode()},
 * not from a register tag.</li>
 * <li>The vessel tag name used for country code, likely
 * {@code country_of_registration}.</li>
 * <li>The vessel tag name used for vessel length, expected to be {@code loa}
 * rather than {@code lbp}.</li>
 * <li>A configured {@code NumericIntervalMapper<String>} for DCF vessel length
 * classes.</li>
 * <li>A configured constant for {@code scale}.</li>
 * <li>A configured constant for {@code model}.</li>
 * </ul>
 *
 * <p>Important domain context:
 *
 * <ul>
 * <li>In the Western Med scenario, register data is loaded through
 * {@code FleetFromVesselRegisterFactory}. Columns that are not explicitly mapped
 * into behaviour/gear/hold/etc. become vessel tags and are therefore the likely
 * source of fleet-segment data.</li>
 * <li>The Western Med fleet register contains both {@code loa} and {@code lbp}.
 * Current expectation is to use {@code loa} for DCF vessel length segmentation.</li>
 * <li>The Western Med scenario already contains a placeholder DCF vessel-length-class
 * mapper declaration built with {@code numericIntervalToStringMapper(...)} and
 * {@code interval(...)}. That is the intended style for wiring the length mapper
 * into the future {@code FleetSegmentMapperFactory}.</li>
 * <li>Some register fields may be missing, {@code NA}, or represented as generic
 * {@code Object} values in tags. The mapper will need a clear policy for converting
 * tag values to strings/numbers and for deciding whether invalid values should
 * become {@code null} or raise an error.</li>
 * </ul>
 *
 * <p>Likely implementation shape:
 *
 * <ul>
 * <li>Immutable runtime object.</li>
 * <li>Constructed by a mutable {@code FleetSegmentMapperFactory}, following the
 * standard project pattern.</li>
 * <li>Public API should probably be either {@code getFleetSegment(Vessel vessel)}
 * or {@code apply(Vessel vessel)}.</li>
 * <li>The mapper may need a few small private helpers to:
 * extract string tags,
 * extract numeric tags,
 * classify vessel length,
 * and assemble the final {@link FleetSegment}.</li>
 * </ul>
 *
 * <p>Likely first tests when implementation starts:
 *
 * <ul>
 * <li>maps gear code from the vessel's actual gear</li>
 * <li>maps country code from a configured vessel tag</li>
 * <li>maps {@code loa} through the numeric interval mapper to a DCF vessel length class</li>
 * <li>uses configured constants for {@code scale} and {@code model}</li>
 * <li>returns {@code null} for missing optional fields where wildcard semantics are desired</li>
 * <li>defines behavior for invalid/non-numeric length values</li>
 * </ul>
 */
public class FleetSegmentMapper {

    public FleetSegmentMapper(
        final String countryCodeTag,
        final String vesselLengthTag,
        final NumericIntervalMapper<String> vesselLengthClassMapper,
        final String scale,
        final String model
    ) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public FleetSegment getFleetSegment(final Vessel vessel) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
