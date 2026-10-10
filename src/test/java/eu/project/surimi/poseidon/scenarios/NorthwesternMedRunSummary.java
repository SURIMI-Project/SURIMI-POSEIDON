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

package eu.project.surimi.poseidon.scenarios;

import eu.project.surimi.poseidon.scenarios.northwesternmed.LocalNorthwesternMedScenario;
import org.joda.money.Money;
import sim.util.Int2D;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEvent;
import uk.ac.ox.poseidon.agents.trips.TripEndEvent;
import uk.ac.ox.poseidon.agents.trips.TripStartEvent;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.events.Listener;
import uk.ac.ox.poseidon.geography.Coordinate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Runs the local Northwestern Mediterranean scenario and prints a one-line summary of what the
 * fleet did, to compare model behaviour before and after a change: trips, hauls, catch, distance
 * from the trip's port to the hauls, distinct fished cells, and trip profit. Optionally also
 * writes the mean retained catch per gear, species and life stage, to compare with
 * {@code target_landings.csv}. Not a test: run its {@code main} from the repository root. Uses only APIs that are stable across the
 * shared-observations work, so that it also runs against older POSEIDON versions.
 */
public class NorthwesternMedRunSummary {

    private static final double EARTH_RADIUS_IN_KM = 6371.0;

    private int trips;
    private int hauls;
    private double grossCatchInKg;
    private double retainedCatchInKg;
    private double distanceFromPortInKm;
    private final Set<Int2D> fishedCells = new HashSet<>();
    private final Map<Vessel, Coordinate> tripOrigins = new HashMap<>();
    private double tripProfit;
    private int endedTrips;
    private final Map<String, Double> retainedKgByKey = new TreeMap<>();

    /**
     * @param args the number of months to run (default 12), the number of runs (default 3), and
     *             optionally a CSV file to write the mean retained catch per gear, species and
     *             life stage to
     */
    public static void main(final String[] args) throws IOException {
        final int months = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        final int runs = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        final Map<String, Double> totalRetainedKgByKey = new TreeMap<>();
        System.out.println(
            "run,trips,hauls,gross_catch_t,retained_catch_t,mean_km_from_port," +
                "distinct_cells,mean_trip_profit"
        );
        for (int run = 1; run <= runs; run++) {
            final NorthwesternMedRunSummary summary = new NorthwesternMedRunSummary();
            System.out.println(run + "," + summary.run(months));
            summary.retainedKgByKey.forEach((key, kg) -> totalRetainedKgByKey.merge(key, kg, Double::sum));
        }
        if (args.length > 2) {
            final List<String> lines = new ArrayList<>();
            lines.add("gear_code,species_code,life_stage,retained_kg");
            totalRetainedKgByKey.forEach((key, kg) -> lines.add(key + "," + kg / runs));
            Files.write(Path.of(args[2]), lines);
        }
    }

    private static double haversineInKm(
        final Coordinate a,
        final Coordinate b
    ) {
        final double dLat = Math.toRadians(b.lat - a.lat);
        final double dLon = Math.toRadians(b.lon - a.lon);
        final double h = Math.pow(Math.sin(dLat / 2), 2) +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
                Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_IN_KM * Math.asin(Math.sqrt(h));
    }

    private String run(final int months) {
        final Simulation simulation = new LocalNorthwesternMedScenario().get().startNewSimulation();
        simulation.getEventManager().addListener(listener(TripStartEvent.class, event -> {
            trips++;
            final Vessel vessel = event.getTrip().getVessel();
            tripOrigins.put(vessel, vessel.getCoordinate());
        }));
        simulation.getEventManager().addListener(listener(FishingEvent.class, event -> {
            final Vessel vessel = event.getAction().getAgent();
            hauls++;
            grossCatchInKg += event.getOutcome().getGrossCatch().getTotalBiomassInKg();
            retainedCatchInKg +=
                event.getOutcome().getDisposition().getRetained().getTotalBiomassInKg();
            distanceFromPortInKm += haversineInKm(
                tripOrigins.get(vessel),
                event.getAction().getStartCoordinate()
            );
            fishedCells.add(vessel.getCell());
            final String gearCode = event.getAction().getGear().getCode();
            event.getOutcome().getDisposition().getRetained().forEachBiomassValue((species, kg) ->
                retainedKgByKey.merge(
                    gearCode + "," + species.getCode() + "," +
                        (species.getLifeStage() == null ? "NA" : species.getLifeStage()),
                    kg,
                    Double::sum
                )
            );
        }));
        simulation.getEventManager().addListener(listener(TripEndEvent.class, event -> {
            endedTrips++;
            for (final Money money : event.getTrip().getAccount().getBalances().values()) {
                tripProfit += money.getAmount().doubleValue();
            }
        }));
        for (int month = 0; month < months; month++) {
            simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
        }
        simulation.finish();
        return "%d,%d,%.1f,%.1f,%.1f,%d,%.0f".formatted(
            trips,
            hauls,
            grossCatchInKg / 1000,
            retainedCatchInKg / 1000,
            hauls == 0 ? 0.0 : distanceFromPortInKm / hauls,
            fishedCells.size(),
            endedTrips == 0 ? 0.0 : tripProfit / endedTrips
        );
    }

    private static <E> Listener<E> listener(
        final Class<E> eventClass,
        final Consumer<E> consumer
    ) {
        return new Listener<>() {
            @Override
            public Class<? extends E> getEventClass() {
                return eventClass;
            }

            @Override
            public void receive(final E event) {
                consumer.accept(event);
            }
        };
    }
}
