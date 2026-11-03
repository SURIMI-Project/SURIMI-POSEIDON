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

import static eu.project.surimi.poseidon.ewe.EweColumns.column;
import static eu.project.surimi.poseidon.ewe.EweColumns.number;
import static eu.project.surimi.poseidon.ewe.EweColumns.wholeNumber;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

/**
 * The biomass entered for the functional groups of an Ecopath model, in
 * tonnes per km², read from a table with the columns of EwE's
 * {@code EcopathGroup} table ({@code GroupID} and {@code Biomass}), wherever
 * that table comes from. A missing or negative biomass, such as EwE's
 * {@code -9999}, is one the user left for Ecopath to estimate during mass
 * balance; it is not known without running Ecopath, so asking for it fails.
 * Column names are matched case-insensitively, and column types are not
 * prescribed (see {@link EweColumns}).
 */
@Getter
@ToString
public final class EcopathBiomasses {

  private static final String GROUP_ID = "GroupID";
  private static final String BIOMASS = "Biomass";

  /**
   * The biomass entered for each group that has one, in tonnes per km², by
   * group ID.
   */
  private final ImmutableMap<Integer, Double> biomasses;

  private EcopathBiomasses(final ImmutableMap<Integer, Double> biomasses) {
    this.biomasses = biomasses;
  }

  /**
   * @param table a table with EwE's {@code EcopathGroup} columns
   * @return the biomass entered for each group that has one
   * @throws IllegalArgumentException if a column is missing or has the wrong
   *                                  kind of values, an ID is not a positive
   *                                  whole number, or two rows have the same
   *                                  ID
   */
  public static EcopathBiomasses fromTable(@NonNull final Table table) {
    final Column<?> biomassColumn = column(table, BIOMASS);
    return new EcopathBiomasses(
      IntStream.range(0, table.rowCount())
        .filter(
          row ->
            !biomassColumn.isMissing(row) && number(biomassColumn, row) >= 0
        )
        .boxed()
        .collect(
          ImmutableMap.toImmutableMap(
            row -> wholeNumber(table, GROUP_ID, row),
            row -> number(biomassColumn, row)
          )
        )
    );
  }

  /**
   * @param groupId the ID of an Ecopath group
   * @return the biomass entered for the group, in tonnes per km²
   * @throws IllegalArgumentException if no biomass was entered for the group
   */
  public double getBiomass(final int groupId) {
    return Optional.ofNullable(biomasses.get(groupId)).orElseThrow(() ->
      new IllegalArgumentException(
        "No biomass entered for Ecopath group " +
          groupId +
          ": Ecopath estimates it, which POSEIDON doesn't do"
      )
    );
  }
}
