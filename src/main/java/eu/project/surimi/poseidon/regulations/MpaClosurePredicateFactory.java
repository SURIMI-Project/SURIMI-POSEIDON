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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.ac.ox.poseidon.agents.vessels.extractors.tags.StringTagExtractor;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.geography.grids.DoubleGridWrapper;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

import java.time.Month;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MpaClosurePredicateFactory<S extends Scope>
    extends RelativeScopeFactory<S, MpaClosurePredicate> {

    private Factory<? super S, ? extends ModelGrid> modelGrid;
    private Factory<? super S, ImmutableMap<String, DoubleGridWrapper>> mpaGrids;
    private Factory<? super S, ImmutableMap<String, ImmutableSet<Month>>> mpaClosedMonths;
    private Factory<? super S, ImmutableMap<String, ImmutableSet<GearCountry>>> mpaFleetRestrictions;
    private String countryTag;

    @Override
    protected MpaClosurePredicate newInstance(final S scope) {
        return new MpaClosurePredicate(
            modelGrid.get(scope),
            mpaGrids.get(scope),
            mpaClosedMonths.get(scope),
            mpaFleetRestrictions.get(scope),
            new StringTagExtractor(countryTag)
        );
    }

}
