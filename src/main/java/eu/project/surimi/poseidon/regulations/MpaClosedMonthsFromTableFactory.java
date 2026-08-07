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
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.scopes.Scope;

import java.time.Month;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

/**
 * Reads a table of {@code (mpa_id, month, closed)} rows and groups it into the set of months each
 * MPA is closed for. An MPA with no {@code closed = TRUE} rows is simply absent from the result;
 * consumers should use {@code getOrDefault(mpaId, ImmutableSet.of())}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MpaClosedMonthsFromTableFactory<S extends Scope>
    extends RelativeScopeFactory<S, ImmutableMap<String, ImmutableSet<Month>>> {

    private Factory<? super S, Table> table;
    private String mpaIdColumnName;
    private String monthColumnName;
    private String closedColumnName;

    @Override
    protected ImmutableMap<String, ImmutableSet<Month>> newInstance(final S scope) {
        return table
            .get(scope)
            .stream()
            .filter(row -> TRUE.equals(row.getBoolean(closedColumnName)))
            .collect(groupingBy(
                row -> row.getString(mpaIdColumnName),
                LinkedHashMap::new,
                mapping(
                    row -> Month.of(row.getInt(monthColumnName)),
                    toCollection(() -> EnumSet.noneOf(Month.class))
                )
            ))
            .entrySet()
            .stream()
            .collect(toImmutableMap(Entry::getKey, e -> Sets.immutableEnumSet(e.getValue())));
    }

}
