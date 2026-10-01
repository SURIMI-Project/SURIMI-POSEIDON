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

import eu.project.surimi.poseidon.server.Server;
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
 * <li>scale from a configured constant</li>
 * <li>model from {@link Server#MODEL_NAME}</li>
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

    public FleetSegmentMapper(
        @NonNull final String countryCodeTag,
        @NonNull final String vesselLengthTag,
        @NonNull final NumericIntervalMapper<String> vesselLengthClassMapper,
        final String scale
    ) {
        this.countryCodeExtractor = new StringTagExtractor(countryCodeTag);
        this.vesselLengthExtractor = new DoubleTagExtractor(vesselLengthTag);
        this.vesselLengthClassMapper = vesselLengthClassMapper;
        this.scale = scale;
    }

    @Override
    public FleetSegment apply(final Vessel vessel) {
        checkNotNull(vessel);

        return new FleetSegment(
            getGearCode(vessel),
            getVesselLengthClass(vessel),
            scale,
            countryCodeExtractor.apply(vessel),
            Server.MODEL_NAME
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
