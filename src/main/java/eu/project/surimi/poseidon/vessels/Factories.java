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

package eu.project.surimi.poseidon.vessels;

import static uk.ac.ox.poseidon.fishing.vessels.Factories.vesselRegisterColumns;
import static uk.ac.ox.poseidon.fishing.vessels.Factories.vesselRegisterEventCodes;

import java.util.List;
import uk.ac.ox.poseidon.fishing.vessels.VesselRegisterColumns;
import uk.ac.ox.poseidon.fishing.vessels.VesselRegisterEventCodes;

/** EU Fleet Register formats for vessel sources in SURIMI scenarios. */
public final class Factories {

  private Factories() {}

  /**
   * @param attributeColumns the columns that become vessel attributes
   * @return the EU Fleet Register's column meanings
   * @see VesselRegisterColumns
   */
  public static VesselRegisterColumns euFleetRegisterColumns(
    final List<String> attributeColumns
  ) {
    return vesselRegisterColumns(
      "cfr",
      "event",
      "event_start_date",
      "place_of_registration",
      attributeColumns
    );
  }

  /**
   * @return the EU Fleet Register's creation, change and exit codes
   * @see VesselRegisterEventCodes
   */
  public static VesselRegisterEventCodes euFleetRegisterEventCodes() {
    return vesselRegisterEventCodes(
      List.of("CEN", "CST", "IMP", "CHA"),
      List.of("MOD"),
      List.of("DES", "EXP", "RET")
    );
  }
}
