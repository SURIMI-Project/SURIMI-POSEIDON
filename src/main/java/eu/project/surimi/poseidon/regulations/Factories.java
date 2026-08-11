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
import eu.project.surimi.poseidon.server.fleet.FleetSegmentMapper;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.core.scopes.SimulationScope;
import uk.ac.ox.poseidon.geography.grids.DoubleGridWrapper;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

import java.time.Month;

public class Factories {

    private Factories() {
    }

    public static TotalAllowableCatchQuotasFactory totalAllowableCatchQuotas() {
        return new TotalAllowableCatchQuotasFactory();
    }

    public static TotalAllowableCatchQuotasFactory totalAllowableCatchQuotas(
        final Factory<? super SimulationScope, ? extends FleetSegmentMapper> fleetSegmentMapper
    ) {
        return new TotalAllowableCatchQuotasFactory(fleetSegmentMapper);
    }

    public static <S extends Scope> MpaClosedMonthsFromTableFactory<S> mpaClosedMonths(
        final Factory<? super S, Table> table,
        final String mpaIdColumnName,
        final String monthColumnName,
        final String closedColumnName
    ) {
        return new MpaClosedMonthsFromTableFactory<>(
            table, mpaIdColumnName, monthColumnName, closedColumnName
        );
    }

    public static <S extends Scope> MpaFleetRestrictionsFromTableFactory<S> mpaFleetRestrictions(
        final Factory<? super S, Table> table,
        final String mpaIdColumnName,
        final String gearCodeColumnName,
        final String countryCodeColumnName
    ) {
        return new MpaFleetRestrictionsFromTableFactory<>(
            table, mpaIdColumnName, gearCodeColumnName, countryCodeColumnName
        );
    }

    public static <S extends Scope> MpaClosurePredicateFactory<S> mpaClosurePredicate(
        final Factory<? super S, ? extends ModelGrid> modelGrid,
        final Factory<? super S, ImmutableMap<String, DoubleGridWrapper>> mpaGrids,
        final Factory<? super S, ImmutableMap<String, ImmutableSet<Month>>> mpaClosedMonths,
        final Factory<? super S, ImmutableMap<String, ImmutableSet<GearCountry>>> mpaFleetRestrictions,
        final String countryTag
    ) {
        return new MpaClosurePredicateFactory<>(
            modelGrid, mpaGrids, mpaClosedMonths, mpaFleetRestrictions, countryTag
        );
    }

    public static <S extends Scope> PortClosuresFromTableFactory<S> portClosures(
        final Factory<? super S, Table> table,
        final String portCodeColumnName,
        final String gearCodeColumnName,
        final String startDateColumnName,
        final String endDateColumnName
    ) {
        return new PortClosuresFromTableFactory<>(
            table, portCodeColumnName, gearCodeColumnName, startDateColumnName, endDateColumnName
        );
    }

    public static <S extends Scope> PortClosurePredicateFactory<S> portClosurePredicate(
        final Factory<? super S, ImmutableMap<String, ImmutableMap<String, ImmutableSet<PortClosureWindow>>>> portClosures
    ) {
        return new PortClosurePredicateFactory<>(portClosures);
    }
}
