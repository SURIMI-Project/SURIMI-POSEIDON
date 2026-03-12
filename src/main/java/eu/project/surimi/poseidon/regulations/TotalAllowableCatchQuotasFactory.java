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

package eu.project.surimi.poseidon.regulations;

import eu.project.surimi.poseidon.server.fleet.FleetSegmentMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEventAccumulator;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.SimulationScopeFactory;
import uk.ac.ox.poseidon.core.scopes.SimulationScope;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Factory for {@link TotalAllowableCatchQuotas} in simulation scope.
 * <p>
 * The regulation depends on a {@link FleetSegmentMapper}, but its lifetime remains pinned to the
 * simulation regardless of the mapper factory's own scope.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TotalAllowableCatchQuotasFactory
    extends SimulationScopeFactory<TotalAllowableCatchQuotas> {

    private Factory<? super SimulationScope, ? extends FleetSegmentMapper> fleetSegmentMapper;

    @Override
    protected TotalAllowableCatchQuotas newInstance(final SimulationScope scope) {
        final FishingEventAccumulator fishingEventAccumulator = new FishingEventAccumulator();
        scope.getSimulation().getEventManager().addListener(fishingEventAccumulator);
        return new TotalAllowableCatchQuotas(
            fishingEventAccumulator,
            checkNotNull(fleetSegmentMapper).get(scope)
        );
    }
}
