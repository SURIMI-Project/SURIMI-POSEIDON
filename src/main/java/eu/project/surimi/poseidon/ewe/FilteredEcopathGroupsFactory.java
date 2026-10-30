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

package eu.project.surimi.poseidon.ewe;

import static com.google.common.base.Preconditions.checkArgument;
import static uk.ac.ox.poseidon.core.functions.VoidContextConversions.toPredicate;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.MappingFactory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.Scope;

/**
 * A {@link RelativeScopeFactory} for the Ecopath groups that pass a predicate,
 * in their original order, such as the groups to make POSEIDON species of.
 * Built via {@link Factories#filteredEcopathGroups(Factory, MappingFactory)}.
 * It fails if no group passes.
 */
@Getter
@ToString
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class FilteredEcopathGroupsFactory<S extends Scope> extends RelativeScopeFactory<
  S,
  ImmutableList<EcopathGroup>
> {

  private final Factory<? super S, ? extends List<EcopathGroup>> groups;
  private final MappingFactory<
    ? super S,
    ? super Void,
    ? super EcopathGroup,
    Boolean
  > predicate;

  @Override
  protected ImmutableList<EcopathGroup> newInstance(final S scope) {
    final List<EcopathGroup> allGroups = groups.get(scope);
    final ImmutableList<EcopathGroup> filteredGroups = allGroups
      .stream()
      .filter(toPredicate(predicate.get(scope)))
      .collect(ImmutableList.toImmutableList());
    checkArgument(
      !filteredGroups.isEmpty(),
      "None of the groups %s passes the predicate",
      allGroups
    );
    return filteredGroups;
  }
}
