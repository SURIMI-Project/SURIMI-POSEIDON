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
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import uk.ac.ox.poseidon.fishing.vessels.HasAttributes;

/**
 * Maps a vessel's attributes to a {@link FleetSegment}: the vessel's current
 * ones when given a vessel, or the ones an action or a sale recorded when given
 * that action or sale, so that what happened is reported under the segment the
 * vessel was in when it happened.
 *
 * <p>
 * The gear code, country code and vessel-length class are read from the
 * attributes; the scale is a configured constant and the model is
 * {@link Server#MODEL_NAME}. A value read as {@code null} is a wildcard, as in
 * {@link FleetSegment#covers(FleetSegment)}: a length outside every length
 * class has no class.
 */
@RequiredArgsConstructor
public final class FleetSegmentMapper
  implements Function<HasAttributes, FleetSegment>
{

  private final @NonNull Function<? super HasAttributes, String> gearCode;
  private final @NonNull Function<? super HasAttributes, String> countryCode;
  private final @NonNull Function<
    ? super HasAttributes,
    String
  > vesselLengthClass;
  private final String scale;

  @Override
  public FleetSegment apply(@NonNull final HasAttributes subject) {
    return new FleetSegment(
      gearCode.apply(subject),
      vesselLengthClass.apply(subject),
      scale,
      countryCode.apply(subject),
      Server.MODEL_NAME
    );
  }
}
