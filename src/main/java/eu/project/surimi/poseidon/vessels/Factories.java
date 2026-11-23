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

import static uk.ac.ox.poseidon.fishing.vessels.Factories.vesselRegister;

import java.util.List;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.SimulationScope;
import uk.ac.ox.poseidon.fishing.vessels.VesselRegisterFactory;
import uk.ac.ox.poseidon.fishing.vessels.VesselSpecification;

/** EU Fleet Register vessel sources in SURIMI scenarios. */
public final class Factories {

  private Factories() {}

  /**
   * @param data                the vessel register table, in the EU Fleet
   *                            Register's format
   * @param vesselSpecification what the vessels are made of
   * @param attributeColumns    the columns that become vessel attributes
   * @return the vessel register for {@code data}, with the EU Fleet Register's
   *         column names and event codes
   * @see uk.ac.ox.poseidon.fishing.vessels.Factories#vesselRegister
   */
  public static VesselRegisterFactory euFleetRegister(
    final Factory<? super SimulationScope, ? extends Table> data,
    final Factory<
      ? super SimulationScope,
      ? extends VesselSpecification
    > vesselSpecification,
    final List<String> attributeColumns
  ) {
    return vesselRegister(
      data,
      vesselSpecification,
      "cfr",
      "event",
      "event_start_date",
      "place_of_registration",
      attributeColumns,
      List.of("CEN", "CST", "IMP", "CHA"),
      List.of("MOD"),
      List.of("DES", "EXP", "RET")
    );
  }
}
