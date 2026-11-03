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

import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.List;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.MappingFactory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.geography.areas.CellAreaCalculator;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

/**
 * Factories for reading EwE models: opening an EwE database, reading its
 * tables, and building POSEIDON components from them. Each extraction has a
 * {@code ...FromTable} helper, which takes any table with the right EwE
 * columns, and a helper without the suffix, which reads that table from an EwE
 * database.
 */
public final class Factories {

  private Factories() {}

  /**
   * @param path the EwE database file
   * @return a {@link RelativeScopeFactory} for the {@link EweDatabase} stored
   *         at {@code path}
   * @see EweDatabase#open(Path)
   */
  public static <S extends Scope> EweDatabaseFactory<S> eweDatabase(
    final Factory<? super S, ? extends Path> path
  ) {
    return new EweDatabaseFactory<>(path);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @param tableName   the EwE table name
   * @return a {@link RelativeScopeFactory} for the table, read once and then
   *         cached
   * @see EweDatabase#table(String)
   */
  public static <S extends Scope> EweTableFactory<S> eweTable(
    final Factory<? super S, ? extends EweDatabase> eweDatabase,
    final String tableName
  ) {
    return new EweTableFactory<>(eweDatabase, tableName);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the database's
   *         {@code EcopathGroup} table, read once and then cached; share it to
   *         read the table once for several extractions
   * @see EcopathGroups#TABLE_NAME
   */
  public static <S extends Scope> EweTableFactory<S> ecopathGroupTable(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return eweTable(eweDatabase, EcopathGroups.TABLE_NAME);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the database's
   *         {@code EcopathCatch} table, read once and then cached; share it to
   *         read the table once for several extractions
   * @see EcopathCatches#TABLE_NAME
   */
  public static <S extends Scope> EweTableFactory<S> ecopathCatchTable(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return eweTable(eweDatabase, EcopathCatches.TABLE_NAME);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the database's
   *         {@code EcospaceScenario} table, read once and then cached; share it
   *         to read the table once for several extractions
   * @see EcospaceBasemap#TABLE_NAME
   */
  public static <S extends Scope> EweTableFactory<S> ecospaceScenarioTable(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return eweTable(eweDatabase, EcospaceBasemap.TABLE_NAME);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the database's
   *         {@code EcospaceScenarioFleet} table, read once and then cached;
   *         share it to read the table once for several extractions
   * @see EcospacePortCells#TABLE_NAME
   */
  public static <S extends Scope> EweTableFactory<S> ecospaceScenarioFleetTable(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return eweTable(eweDatabase, EcospacePortCells.TABLE_NAME);
  }

  /**
   * @param table any table with the columns of EwE's {@code EcopathGroup}
   *              table
   * @return a {@link RelativeScopeFactory} for the model's functional groups,
   *         in the order of the table's rows
   * @see EcopathGroups#fromTable(Table)
   */
  public static <S extends Scope> EcopathGroupsFromTableFactory<S> ecopathGroupsFromTable(
    final Factory<? super S, ? extends Table> table
  ) {
    return new EcopathGroupsFromTableFactory<>(table);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the model's functional groups,
   *         read from the database's {@code EcopathGroup} table
   * @see EcopathGroups#fromTable(Table)
   */
  public static <S extends Scope> EcopathGroupsFromTableFactory<S> ecopathGroups(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return ecopathGroupsFromTable(ecopathGroupTable(eweDatabase));
  }

  /**
   * @param table any table with the columns of EwE's {@code EcopathCatch}
   *              table
   * @return a {@link RelativeScopeFactory} for the model's Ecopath catches
   * @see EcopathCatches#fromTable(Table)
   */
  public static <S extends Scope> EcopathCatchesFromTableFactory<S> ecopathCatchesFromTable(
    final Factory<? super S, ? extends Table> table
  ) {
    return new EcopathCatchesFromTableFactory<>(table);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the model's Ecopath catches,
   *         read from the database's {@code EcopathCatch} table
   * @see EcopathCatches#fromTable(Table)
   */
  public static <S extends Scope> EcopathCatchesFromTableFactory<S> ecopathCatches(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return ecopathCatchesFromTable(ecopathCatchTable(eweDatabase));
  }

  /**
   * @param ecopathCatches the Ecopath catches of the model
   * @return a {@link MappingFactory} for a yes/no mapping of groups that is
   *         true iff some fleet lands or discards some of the group
   * @see CaughtInEcopath
   */
  public static <S extends Scope> CaughtInEcopathFactory<S> caughtInEcopath(
    final Factory<? super S, ? extends EcopathCatches> ecopathCatches
  ) {
    return new CaughtInEcopathFactory<>(ecopathCatches);
  }

  /**
   * @param propertyPath the dotted path of properties to follow from the
   *                     group, e.g. {@code "id"} or {@code "name"}
   * @return a {@link uk.ac.ox.poseidon.core.GlobalScopeFactory} for an
   *         {@link uk.ac.ox.poseidon.core.functions.ObjectProperty} of an
   *         Ecopath group
   * @see EcopathGroupPropertyFactory
   */
  public static <R> EcopathGroupPropertyFactory<R> ecopathGroupProperty(
    final String propertyPath
  ) {
    return new EcopathGroupPropertyFactory<>(propertyPath);
  }

  /**
   * @param ecopathGroups the functional groups of an Ecopath model
   * @param predicate     the yes/no mapping a group must pass to be kept
   * @return a {@link RelativeScopeFactory} for the groups that pass the
   *         predicate, in their original order
   * @see FilteredEcopathGroupsFactory
   */
  public static <S extends Scope> FilteredEcopathGroupsFactory<S> filteredEcopathGroups(
    final Factory<? super S, ? extends List<EcopathGroup>> ecopathGroups,
    final MappingFactory<
      ? super S,
      ? super Void,
      ? super EcopathGroup,
      Boolean
    > predicate
  ) {
    return new FilteredEcopathGroupsFactory<>(ecopathGroups, predicate);
  }

  /**
   * @param ecopathGroups the functional groups of an Ecopath model
   * @return a {@link RelativeScopeFactory} for an
   *         {@link EcopathSpeciesMapping} that maps each group to a species of
   *         its own
   * @see EcopathSpeciesMapping#fromGroups(List)
   */
  public static <S extends Scope> EcopathSpeciesMappingFromGroupsFactory<S> ecopathSpeciesMappingFromGroups(
    final Factory<? super S, ? extends List<EcopathGroup>> ecopathGroups
  ) {
    return new EcopathSpeciesMappingFromGroupsFactory<>(ecopathGroups);
  }

  /**
   * @param ecopathSpeciesMapping a mapping of Ecopath groups to species
   * @return a {@link RelativeScopeFactory} for the mapping's species, in the
   *         mapping's order
   * @see SpeciesFromEcopathSpeciesMappingFactory
   */
  public static <S extends Scope> SpeciesFromEcopathSpeciesMappingFactory<S> speciesFromEcopathSpeciesMapping(
    final Factory<
      ? super S,
      ? extends EcopathSpeciesMapping
    > ecopathSpeciesMapping
  ) {
    return new SpeciesFromEcopathSpeciesMappingFactory<>(ecopathSpeciesMapping);
  }

  /**
   * @param table                any table with the columns of EwE's
   *                             {@code EcospaceScenario} table
   * @param ecospaceScenarioName the name of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the scenario's
   *         {@link EcospaceBasemap}
   * @see EcospaceBasemap#fromTable(Table, String)
   */
  public static <S extends Scope> EcospaceBasemapFromTableFactory<S> ecospaceBasemapFromTable(
    final Factory<? super S, ? extends Table> table,
    final String ecospaceScenarioName
  ) {
    return new EcospaceBasemapFromTableFactory<>(table, ecospaceScenarioName);
  }

  /**
   * @param eweDatabase          the EwE database to read from
   * @param ecospaceScenarioName the name of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the scenario's
   *         {@link EcospaceBasemap}, read from the database's
   *         {@code EcospaceScenario} table
   * @see EcospaceBasemap#fromTable(Table, String)
   */
  public static <S extends Scope> EcospaceBasemapFromTableFactory<S> ecospaceBasemap(
    final Factory<? super S, ? extends EweDatabase> eweDatabase,
    final String ecospaceScenarioName
  ) {
    return ecospaceBasemapFromTable(
      ecospaceScenarioTable(eweDatabase),
      ecospaceScenarioName
    );
  }

  /**
   * @param table           any table with the columns of EwE's
   *                        {@code EcospaceScenarioFleet} table
   * @param ecospaceBasemap the base map of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the cells that are a port of at
   *         least one of the scenario's fleets
   * @see EcospacePortCells#fromTable(Table, EcospaceBasemap)
   */
  public static <S extends Scope> EcospacePortCellsFromTableFactory<S> ecospacePortCellsFromTable(
    final Factory<? super S, ? extends Table> table,
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap
  ) {
    return new EcospacePortCellsFromTableFactory<>(table, ecospaceBasemap);
  }

  /**
   * @param eweDatabase     the EwE database to read from
   * @param ecospaceBasemap the base map of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the cells that are a port of at
   *         least one of the scenario's fleets, read from the database's
   *         {@code EcospaceScenarioFleet} table
   * @see EcospacePortCells#fromTable(Table, EcospaceBasemap)
   */
  public static <S extends Scope> EcospacePortCellsFromTableFactory<S> ecospacePortCells(
    final Factory<? super S, ? extends EweDatabase> eweDatabase,
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap
  ) {
    return ecospacePortCellsFromTable(
      ecospaceScenarioFleetTable(eweDatabase),
      ecospaceBasemap
    );
  }

  /**
   * @param ecospaceBasemap the Ecospace base map
   * @return a {@link RelativeScopeFactory} for a {@link ModelGrid} with the
   *         base map's dimensions and envelope, whose active cells are those
   *         the base map doesn't exclude
   * @see ModelGridFromEcospaceBasemapFactory
   */
  public static <S extends Scope> ModelGridFromEcospaceBasemapFactory<S> modelGridFromEcospaceBasemap(
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap
  ) {
    return new ModelGridFromEcospaceBasemapFactory<>(ecospaceBasemap);
  }

  /**
   * @param ecospaceBasemap the Ecospace base map
   * @param modelGrid       a model grid with the base map's dimensions and
   *                        envelope
   * @return a {@link RelativeScopeFactory} for the {@link BathymetricGrid} of
   *         the base map's depths
   * @see BathymetricGridFromEcospaceBasemapFactory
   */
  public static <S extends Scope> BathymetricGridFromEcospaceBasemapFactory<S> bathymetricGridFromEcospaceBasemap(
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap,
    final Factory<? super S, ? extends ModelGrid> modelGrid
  ) {
    return new BathymetricGridFromEcospaceBasemapFactory<>(
      ecospaceBasemap,
      modelGrid
    );
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the database's
   *         {@code EcospaceScenarioGroup} table, read once and then cached;
   *         share it to read the table once for several extractions
   * @see EcospaceGroups#TABLE_NAME
   */
  public static <S extends Scope> EweTableFactory<S> ecospaceScenarioGroupTable(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return eweTable(eweDatabase, EcospaceGroups.TABLE_NAME);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the database's
   *         {@code EcospaceScenarioHabitat} table, read once and then cached;
   *         share it to read the table once for several extractions
   * @see EcospaceHabitats#HABITAT_TABLE_NAME
   */
  public static <S extends Scope> EweTableFactory<S> ecospaceScenarioHabitatTable(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return eweTable(eweDatabase, EcospaceHabitats.HABITAT_TABLE_NAME);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the database's
   *         {@code EcospaceScenarioGroupHabitat} table, read once and then
   *         cached; share it to read the table once for several extractions
   * @see EcospaceHabitats#GROUP_HABITAT_TABLE_NAME
   */
  public static <S extends Scope> EweTableFactory<S> ecospaceScenarioGroupHabitatTable(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return eweTable(eweDatabase, EcospaceHabitats.GROUP_HABITAT_TABLE_NAME);
  }

  /**
   * @param table           any table with the columns of EwE's
   *                        {@code EcospaceScenarioGroup} table
   * @param ecospaceBasemap the base map of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the scenario's groups, by the
   *         ID of their Ecopath group
   * @see EcospaceGroups#fromTable(Table, EcospaceBasemap)
   */
  public static <S extends Scope> EcospaceGroupsFromTableFactory<S> ecospaceGroupsFromTable(
    final Factory<? super S, ? extends Table> table,
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap
  ) {
    return new EcospaceGroupsFromTableFactory<>(table, ecospaceBasemap);
  }

  /**
   * @param eweDatabase     the EwE database to read from
   * @param ecospaceBasemap the base map of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the scenario's groups, by the
   *         ID of their Ecopath group, read from the database's
   *         {@code EcospaceScenarioGroup} table
   * @see EcospaceGroups#fromTable(Table, EcospaceBasemap)
   */
  public static <S extends Scope> EcospaceGroupsFromTableFactory<S> ecospaceGroups(
    final Factory<? super S, ? extends EweDatabase> eweDatabase,
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap
  ) {
    return ecospaceGroupsFromTable(
      ecospaceScenarioGroupTable(eweDatabase),
      ecospaceBasemap
    );
  }

  /**
   * @param habitatTable      any table with the columns of EwE's
   *                          {@code EcospaceScenarioHabitat} table
   * @param groupHabitatTable any table with the columns of EwE's
   *                          {@code EcospaceScenarioGroupHabitat} table
   * @param ecospaceBasemap   the base map of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the scenario's habitats and the
   *         groups' preferences
   * @see EcospaceHabitats#fromTables(Table, Table, EcospaceBasemap)
   */
  public static <S extends Scope> EcospaceHabitatsFromTablesFactory<S> ecospaceHabitatsFromTables(
    final Factory<? super S, ? extends Table> habitatTable,
    final Factory<? super S, ? extends Table> groupHabitatTable,
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap
  ) {
    return new EcospaceHabitatsFromTablesFactory<>(
      habitatTable,
      groupHabitatTable,
      ecospaceBasemap
    );
  }

  /**
   * @param eweDatabase     the EwE database to read from
   * @param ecospaceBasemap the base map of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the scenario's habitats and the
   *         groups' preferences, read from the database's
   *         {@code EcospaceScenarioHabitat} and
   *         {@code EcospaceScenarioGroupHabitat} tables
   * @see EcospaceHabitats#fromTables(Table, Table, EcospaceBasemap)
   */
  public static <S extends Scope> EcospaceHabitatsFromTablesFactory<S> ecospaceHabitats(
    final Factory<? super S, ? extends EweDatabase> eweDatabase,
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap
  ) {
    return ecospaceHabitatsFromTables(
      ecospaceScenarioHabitatTable(eweDatabase),
      ecospaceScenarioGroupHabitatTable(eweDatabase),
      ecospaceBasemap
    );
  }

  /**
   * @param table any table with the columns of EwE's {@code EcopathGroup}
   *              table
   * @return a {@link RelativeScopeFactory} for the biomass entered for the
   *         model's groups
   * @see EcopathBiomasses#fromTable(Table)
   */
  public static <S extends Scope> EcopathBiomassesFromTableFactory<S> ecopathBiomassesFromTable(
    final Factory<? super S, ? extends Table> table
  ) {
    return new EcopathBiomassesFromTableFactory<>(table);
  }

  /**
   * @param eweDatabase the EwE database to read from
   * @return a {@link RelativeScopeFactory} for the biomass entered for the
   *         model's groups, read from the database's {@code EcopathGroup}
   *         table
   * @see EcopathBiomasses#fromTable(Table)
   */
  public static <S extends Scope> EcopathBiomassesFromTableFactory<S> ecopathBiomasses(
    final Factory<? super S, ? extends EweDatabase> eweDatabase
  ) {
    return ecopathBiomassesFromTable(ecopathGroupTable(eweDatabase));
  }

  /**
   * @param ecospaceBasemap       the base map of the Ecospace scenario
   * @param ecospaceGroups        the scenario's groups, by Ecopath group ID
   * @param ecospaceHabitats      the scenario's habitats and the groups'
   *                              preferences
   * @param ecopathBiomasses      the biomass entered for the Ecopath groups
   * @param cellAreaCalculator    POSEIDON's areas of the model grid's cells
   * @param ecopathSpeciesMapping how the groups' biomass is shared between
   *                              species
   * @param species               the species to give a starting biomass to
   * @return a {@link RelativeScopeFactory} for one allocator of starting
   *         biomass per species, in the order of {@code species}
   * @see BiomassAllocatorsFromEcospaceFactory
   * @see EcospaceStartingBiomass
   */
  public static <S extends Scope> BiomassAllocatorsFromEcospaceFactory<S> biomassAllocatorsFromEcospace(
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap,
    final Factory<
      ? super S,
      ? extends ImmutableMap<Integer, EcospaceGroup>
    > ecospaceGroups,
    final Factory<? super S, ? extends EcospaceHabitats> ecospaceHabitats,
    final Factory<? super S, ? extends EcopathBiomasses> ecopathBiomasses,
    final Factory<? super S, ? extends CellAreaCalculator> cellAreaCalculator,
    final Factory<
      ? super S,
      ? extends EcopathSpeciesMapping
    > ecopathSpeciesMapping,
    final Factory<? super S, ? extends List<? extends Species>> species
  ) {
    return new BiomassAllocatorsFromEcospaceFactory<>(
      ecospaceBasemap,
      ecospaceGroups,
      ecospaceHabitats,
      ecopathBiomasses,
      cellAreaCalculator,
      ecopathSpeciesMapping,
      species
    );
  }

  /**
   * @param eweDatabase           the EwE database to read from
   * @param ecospaceBasemap       the base map of the Ecospace scenario
   * @param cellAreaCalculator    POSEIDON's areas of the model grid's cells
   * @param ecopathSpeciesMapping how the groups' biomass is shared between
   *                              species
   * @param species               the species to give a starting biomass to
   * @return a {@link RelativeScopeFactory} for one allocator of starting
   *         biomass per species, in the order of {@code species}, reading the
   *         groups, habitats, biomasses and cell areas from the database
   * @see BiomassAllocatorsFromEcospaceFactory
   * @see EcospaceStartingBiomass
   */
  public static <S extends Scope> BiomassAllocatorsFromEcospaceFactory<S> biomassAllocatorsFromEcospace(
    final Factory<? super S, ? extends EweDatabase> eweDatabase,
    final Factory<? super S, ? extends EcospaceBasemap> ecospaceBasemap,
    final Factory<? super S, ? extends CellAreaCalculator> cellAreaCalculator,
    final Factory<
      ? super S,
      ? extends EcopathSpeciesMapping
    > ecopathSpeciesMapping,
    final Factory<? super S, ? extends List<? extends Species>> species
  ) {
    return biomassAllocatorsFromEcospace(
      ecospaceBasemap,
      ecospaceGroups(eweDatabase, ecospaceBasemap),
      ecospaceHabitats(eweDatabase, ecospaceBasemap),
      ecopathBiomasses(eweDatabase),
      cellAreaCalculator,
      ecopathSpeciesMapping,
      species
    );
  }
}
