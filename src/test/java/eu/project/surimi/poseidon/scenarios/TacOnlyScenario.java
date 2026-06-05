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

package eu.project.surimi.poseidon.scenarios;

import uk.ac.ox.poseidon.core.Scenario;

import java.time.LocalDateTime;
import java.util.function.Supplier;

import static eu.project.surimi.poseidon.regulations.Factories.totalAllowableCatchQuotas;
import static eu.project.surimi.poseidon.server.fleet.Factories.fleetSegmentMapper;
import static uk.ac.ox.poseidon.core.functions.Factories.numericIntervalToStringMapper;
import static uk.ac.ox.poseidon.core.functions.NumericIntervalToStringMapperFactory.interval;
import static uk.ac.ox.poseidon.core.time.Factories.dateTime;

public class TacOnlyScenario implements Supplier<Scenario> {
    @Override
    public Scenario get() {
        return Scenario.builder()
            .startingDateTime(dateTime(LocalDateTime.of(2000, 1, 1, 0, 0)))
            .component(
                "tac",
                totalAllowableCatchQuotas(
                    fleetSegmentMapper(
                        "country_of_registration",
                        "loa",
                        numericIntervalToStringMapper(
                            interval(12.0, 18.0, "VL1218"),
                            interval(18.0, 24.0, "VL1824")
                        ),
                        "Industrial",
                        "POSEIDON"
                    )
                )
            )
            .build();
    }
}
