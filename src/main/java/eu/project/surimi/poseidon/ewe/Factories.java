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

import java.nio.file.Path;
import tech.tablesaw.api.Table;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.Scope;
import uk.ac.ox.poseidon.geography.bathymetry.BathymetricGrid;
import uk.ac.ox.poseidon.geography.grids.ModelGrid;

/**
 * Factories for reading EwE models: opening an EwE database, reading its
 * tables, and building POSEIDON components from them. Each extraction takes any
 * table with the right EwE columns, and comes with a {@code ...FromEweDatabase}
 * helper that reads that table from an EwE database.
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
   * @param table                any table with the columns of EwE's
   *                             {@code EcospaceScenario} table
   * @param ecospaceScenarioName the name of the Ecospace scenario
   * @return a {@link RelativeScopeFactory} for the scenario's
   *         {@link EcospaceBasemap}
   * @see EcospaceBasemap#fromTable(Table, String)
   */
  public static <S extends Scope> EcospaceBasemapFromTableFactory<S> ecospaceBasemap(
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
  public static <S extends Scope> EcospaceBasemapFromTableFactory<S> ecospaceBasemapFromEweDatabase(
    final Factory<? super S, ? extends EweDatabase> eweDatabase,
    final String ecospaceScenarioName
  ) {
    return ecospaceBasemap(
      ecospaceScenarioTable(eweDatabase),
      ecospaceScenarioName
    );
  }

  /**
   * @param ecospaceBasemap the Ecospace base map
   * @return a {@link RelativeScopeFactory} for a {@link ModelGrid} with the
   *         base map's dimensions and envelope, with every cell active
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
}
