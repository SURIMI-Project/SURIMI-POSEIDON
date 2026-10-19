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
import com.google.common.collect.ImmutableSortedMap;
import java.time.Year;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.MappingFactory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.core.ValueFactory;
import uk.ac.ox.poseidon.fishing.regulations.actions.ExtendedFishingAction;
import uk.ac.ox.poseidon.fishing.vessels.HasAttributes;

/**
 * Factory for {@link PortClosurePredicate}, sourcing its port closure windows
 * from a table, with the home port and gear codes read from the attributes an
 * action recorded.
 */
@Getter
@ToString
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class PortClosurePredicateFactory<S extends Scope>
  extends RelativeScopeFactory<
    S,
    BiFunction<Object, ExtendedFishingAction, Boolean>
  >
  implements MappingFactory<S, Object, ExtendedFishingAction, Boolean>
{

  private final Factory<
    ? super S,
    ImmutableMap<
      String,
      ImmutableMap<
        String,
        ImmutableSortedMap<Year, ImmutableSet<PortClosureWindow>>
      >
    >
  > portClosures;
  private final ValueFactory<
    ? super S,
    ? super HasAttributes,
    String
  > homePortCode;
  private final ValueFactory<? super S, ? super HasAttributes, String> gearCode;

  @Override
  protected BiFunction<Object, ExtendedFishingAction, Boolean> newInstance(
    final S scope
  ) {
    return new PortClosurePredicate(
      portClosures.get(scope),
      homePortCode.get(scope),
      gearCode.get(scope)
    );
  }
}
