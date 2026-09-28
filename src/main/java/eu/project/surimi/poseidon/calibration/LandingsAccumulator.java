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

package eu.project.surimi.poseidon.calibration;

import com.google.common.collect.ComparisonChain;
import tech.tablesaw.api.*;
import uk.ac.ox.poseidon.agents.market.Sale;
import uk.ac.ox.poseidon.core.events.AbstractListener;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;
import static uk.ac.ox.poseidon.core.utils.Utils.multiStringKey;

/**
 * Accumulates landed biomass from {@link Sale} events into a running total per year, gear, and
 * species/life-stage key, for comparison against real-world landings targets during calibration
 * (see {@code NorthwesternMedCalibration}).
 */
public class LandingsAccumulator extends AbstractListener<Sale>
    implements Supplier<Map<LandingsAccumulator.Key, Double>> {

    private final Map<LandingsAccumulator.Key, Double> landings = new TreeMap<>();

    public LandingsAccumulator() {
        super(Sale.class);
    }

    /**
     * Adds the sale's items to the running per-year/gear/species landings total.
     */
    @Override
    public void receive(final Sale sale) {
        final int year = sale.getDateTime().getYear();
        final String gearCode = sale.getVessel().getGear().getCode();
        sale
            .getItems()
            .stream()
            .collect(toMap(
                    item ->
                        new Key(
                            year,
                            gearCode,
                            multiStringKey(
                                item.getSpecies().getCode(),
                                item.getSpecies().getLifeStage()
                            )
                        ),
                    item -> item.getContent().asKg(),
                    Double::sum
                )
            )
            .forEach((key, value) ->
                landings.merge(key, value, Double::sum)
            );
    }

    /**
     * @return an unmodifiable view of the accumulated landings, keyed by year/gear/species.
     */
    @Override
    public Map<Key, Double> get() {
        return Collections.unmodifiableMap(landings);
    }

    /**
     * Year, gear code, and species/life-stage key ({@link #speciesKey} is built with
     * {@link uk.ac.ox.poseidon.core.utils.Utils#multiStringKey}) identifying one accumulated
     * landings total.
     */
    public record Key(
        Integer year, String gearCode, String speciesKey
    ) implements Comparable<Key> {
        @Override
        public int compareTo(final Key other) {
            return ComparisonChain.start()
                .compare(this.year, other.year)
                .compare(this.gearCode, other.gearCode)
                .compare(this.speciesKey, other.speciesKey)
                .result();
        }
    }

    /**
     * @return the accumulated landings as a table with columns {@code year}, {@code gear_code},
     * {@code species_code}, {@code landings_kg}.
     */
    public Table asTable() {
        final IntColumn year = ColumnType.INTEGER.create("year");
        final StringColumn gearCode = ColumnType.STRING.create("gear_code");
        final StringColumn speciesCode = ColumnType.STRING.create("species_code");
        final DoubleColumn landingsKg = ColumnType.DOUBLE.create("landings_kg");
        landings.forEach((key, value) -> {
            year.append(key.year);
            gearCode.append(key.gearCode);
            speciesCode.append(key.speciesKey);
            landingsKg.append(value);
        });
        return Table.create(year, gearCode, speciesCode, landingsKg);
    }

}
