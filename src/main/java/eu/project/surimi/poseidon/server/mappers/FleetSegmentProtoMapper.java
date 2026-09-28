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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

/**
 * Converts between {@link eu.project.surimi.poseidon.server.fleet.FleetSegment} and the
 * {@code surimi.v1.FleetSegment} proto. Each dimension is a wildcard when blank or unset on
 * either side, so blank/unset is normalised to {@code null} in both directions.
 */
public final class FleetSegmentProtoMapper {

    private FleetSegmentProtoMapper() {
    }

    /**
     * @return the POSEIDON {@link eu.project.surimi.poseidon.server.fleet.FleetSegment} for a
     * proto fleet segment, with each blank dimension normalised to {@code null}.
     */
    public static eu.project.surimi.poseidon.server.fleet.FleetSegment toPoseidonFleetSegment(
        final build.buf.gen.surimi.v1.FleetSegment fleetSegment
    ) {
        final build.buf.gen.surimi.v1.FleetSegment protoFleetSegment = checkNotNull(fleetSegment);
        return new eu.project.surimi.poseidon.server.fleet.FleetSegment(
            nullIfBlank(protoFleetSegment.getGearCode()),
            nullIfBlank(protoFleetSegment.getVesselLengthClass()),
            nullIfBlank(protoFleetSegment.getScale()),
            nullIfBlank(protoFleetSegment.getCountryCode()),
            nullIfBlank(protoFleetSegment.getModel())
        );
    }

    /**
     * @return the proto fleet segment for a POSEIDON
     * {@link eu.project.surimi.poseidon.server.fleet.FleetSegment}, leaving each wildcard ({@code
     * null}) dimension unset.
     */
    public static build.buf.gen.surimi.v1.FleetSegment toProtoFleetSegment(
        final eu.project.surimi.poseidon.server.fleet.FleetSegment fleetSegment
    ) {
        final eu.project.surimi.poseidon.server.fleet.FleetSegment poseidonFleetSegment =
            checkNotNull(fleetSegment);
        final build.buf.gen.surimi.v1.FleetSegment.Builder builder =
            build.buf.gen.surimi.v1.FleetSegment.newBuilder();
        setIfPresent(builder::setGearCode, poseidonFleetSegment.getGearCode());
        setIfPresent(builder::setVesselLengthClass, poseidonFleetSegment.getVesselLengthClass());
        setIfPresent(builder::setScale, poseidonFleetSegment.getScale());
        setIfPresent(builder::setCountryCode, poseidonFleetSegment.getCountryCode());
        setIfPresent(builder::setModel, poseidonFleetSegment.getModel());
        return builder.build();
    }

    private static void setIfPresent(
        final java.util.function.Consumer<String> setter,
        final String value
    ) {
        final String normalisedValue = nullIfBlank(value);
        if (normalisedValue != null) {
            setter.accept(normalisedValue);
        }
    }

    private static String nullIfBlank(final String value) {
        return value == null ? null : emptyToNull(value.strip());
    }
}
