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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.joda.money.Money;
import sim.util.Int2D;
import uk.ac.ox.poseidon.agents.social.SocialNetwork;
import uk.ac.ox.poseidon.agents.tasks.fishing.FishingEvent;
import uk.ac.ox.poseidon.agents.trips.TripEndEvent;
import uk.ac.ox.poseidon.agents.trips.TripStartEvent;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.SimulationStartOptions;
import uk.ac.ox.poseidon.core.events.Listener;
import uk.ac.ox.poseidon.geography.Coordinate;

/**
 * Runs the local Northwestern Mediterranean scenario and prints a one-line summary of what the
 * fleet did, to compare model behaviour before and after a change: the schedule's date-time before
 * the first step and after the last, trips, fishing events, catch, distance from the trip's port
 * to the fishing actions, distinct fished cells, and trip profit; and, per gear, the vessels that
 * made a trip, the ended trips, their fishing events and hours, trips per vessel-day, and how many
 * trips had 0, 1, 2... fishing events. Optionally also
 * writes the mean retained catch per gear, species and life stage, to compare with
 * {@code target_landings.csv}. Each run's seed is its number, so the runs differ from each other
 * and an invocation can be repeated. The runs run in parallel, so {@code seconds} is each run's wall
 * time while sharing the machine. Not a test: run its {@code main} from the repository root. Uses only APIs that are stable across the
 * shared-observations work, so that it also runs against older POSEIDON versions.
 */
public class NorthwesternMedRunSummary {

  private static final double EARTH_RADIUS_IN_KM = 6371.0;

  private final long seed;

  private int trips;
  private int fishingEvents;
  private double grossCatchInKg;
  private double retainedCatchInKg;
  private double distanceFromPortInKm;
  private final Set<Int2D> fishedCells = new HashSet<>();
  private final Map<Vessel, Coordinate> tripOrigins = new HashMap<>();
  private double tripProfit;
  private int endedTrips;
  private final Map<String, Double> retainedKgByKey = new TreeMap<>();
  private long sharedFishingEvents;
  private final Map<Vessel, Integer> fishingEventsThisTrip = new HashMap<>();
  private final Map<String, GearSummary> gearSummaries = new TreeMap<>();

  /** What the vessels of one gear did, counted over the trips that ended. */
  private static class GearSummary {

    private final Set<Vessel> vessels = new HashSet<>();
    private int trips;
    private int fishingEvents;
    private double hours;
    private final Map<Integer, Integer> tripsByFishingEvents = new TreeMap<>();

    private String format(final double days) {
      final StringBuilder histogram = new StringBuilder();
      tripsByFishingEvents.forEach((events, count) ->
        histogram
          .append(histogram.isEmpty() ? "" : " ")
          .append(events)
          .append(':')
          .append(count)
      );
      return "vessels=%d trips=%d events/trip=%.2f h/trip=%.1f trips/vessel-day=%.2f events:trips=%s".formatted(
        vessels.size(),
        trips,
        trips == 0 ? 0.0 : (double) fishingEvents / trips,
        trips == 0 ? 0.0 : hours / trips,
        vessels.isEmpty() ? 0.0 : trips / (vessels.size() * days),
        histogram
      );
    }
  }

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
      "run,start,end,trips,fishing_events,gross_catch_t,retained_catch_t,mean_km_from_port," +
        "distinct_cells,mean_trip_profit,shared_per_fishing_event,clique_sizes,seconds,by_gear"
    );
    // The runs are independent simulations, so they run in parallel; each line is printed
    // once all are done, in run order.
    final List<NorthwesternMedRunSummary> summaries = IntStream.rangeClosed(
      1,
      runs
    )
      .mapToObj(NorthwesternMedRunSummary::new)
      .toList();
    final List<String> lines = summaries
      .parallelStream()
      .map(summary -> summary.run(months))
      .toList();
    for (int run = 1; run <= runs; run++) {
      System.out.println(run + "," + lines.get(run - 1));
      summaries
        .get(run - 1)
        .retainedKgByKey.forEach((key, kg) ->
          totalRetainedKgByKey.merge(key, kg, Double::sum)
        );
    }
    if (args.length > 2) {
      final List<String> csvLines = new ArrayList<>();
      csvLines.add("gear_code,species_code,life_stage,retained_kg");
      totalRetainedKgByKey.forEach((key, kg) ->
        csvLines.add(key + "," + kg / runs)
      );
      Files.write(Path.of(args[2]), csvLines);
    }
  }

  /** @param seed the seed of this summary's simulation */
  private NorthwesternMedRunSummary(final long seed) {
    this.seed = seed;
  }

  private static double haversineInKm(final Coordinate a, final Coordinate b) {
    final double dLat = Math.toRadians(b.lat - a.lat);
    final double dLon = Math.toRadians(b.lon - a.lon);
    final double h =
      Math.pow(Math.sin(dLat / 2), 2) +
      Math.cos(Math.toRadians(a.lat)) *
        Math.cos(Math.toRadians(b.lat)) *
        Math.pow(Math.sin(dLon / 2), 2);
    return 2 * EARTH_RADIUS_IN_KM * Math.asin(Math.sqrt(h));
  }

  private String run(final int months) {
    final long startNanos = System.nanoTime();
    final Simulation simulation = new LocalNorthwesternMedScenario()
      .get()
      .startNewSimulation(SimulationStartOptions.builder().seed(seed).build());
    final SocialNetwork network = simulation.getComponent(SocialNetwork.class);
    simulation.getEventManager().addListener(
      listener(TripStartEvent.class, event -> {
        trips++;
        final Vessel vessel = event.getTrip().getVessel();
        tripOrigins.put(vessel, vessel.getCoordinate());
        fishingEventsThisTrip.put(vessel, 0);
      })
    );
    simulation.getEventManager().addListener(
      listener(FishingEvent.class, event -> {
        final Vessel vessel = event.getAction().getAgent();
        fishingEvents++;
        fishingEventsThisTrip.merge(vessel, 1, Integer::sum);
        sharedFishingEvents += network.getRecipients(vessel).size();
        grossCatchInKg += event
          .getOutcome()
          .getGrossCatch()
          .getTotalBiomassInKg();
        retainedCatchInKg += event
          .getOutcome()
          .getDisposition()
          .getRetained()
          .getTotalBiomassInKg();
        distanceFromPortInKm += haversineInKm(
          tripOrigins.get(vessel),
          event.getAction().getStartCoordinate()
        );
        fishedCells.add(vessel.getCell());
        final String gearCode = event.getAction().getGear().getCode();
        event
          .getOutcome()
          .getDisposition()
          .getRetained()
          .forEachBiomassValue((species, kg) ->
            retainedKgByKey.merge(
              gearCode +
                "," +
                species.getCode() +
                "," +
                (species.getLifeStage() == null
                  ? "NA"
                  : species.getLifeStage()),
              kg,
              Double::sum
            )
          );
      })
    );
    simulation.getEventManager().addListener(
      listener(TripEndEvent.class, event -> {
        endedTrips++;
        final Vessel vessel = event.getTrip().getVessel();
        final GearSummary gearSummary = gearSummaries.computeIfAbsent(
          vessel.getGear().getCode(),
          code -> new GearSummary()
        );
        final int tripFishingEvents = fishingEventsThisTrip.remove(vessel);
        gearSummary.vessels.add(vessel);
        gearSummary.trips++;
        gearSummary.fishingEvents += tripFishingEvents;
        gearSummary.hours +=
          event.getTrip().getTimeAtSea().toSeconds() / 3600.0;
        gearSummary.tripsByFishingEvents.merge(
          tripFishingEvents,
          1,
          Integer::sum
        );
        for (final Money money : event
          .getTrip()
          .getAccount()
          .getBalances()
          .values()) {
          tripProfit += money.getAmount().doubleValue();
        }
      })
    );
    final LocalDateTime startDateTime = simulation
      .getTemporalSchedule()
      .getDateTime();
    for (int month = 0; month < months; month++) {
      simulation.getTemporalSchedule().stepFor(simulation, Period.ofMonths(1));
    }
    final LocalDateTime endDateTime = simulation
      .getTemporalSchedule()
      .getDateTime();
    // Clique sizes at the end of the run, over the vessels that made a trip: "size:vessels".
    final Map<Integer, Integer> vesselsByCliqueSize = new TreeMap<>();
    tripOrigins
      .keySet()
      .forEach(vessel ->
        vesselsByCliqueSize.merge(
          network.getRecipients(vessel).size() + 1,
          1,
          Integer::sum
        )
      );
    final StringBuilder cliqueSizes = new StringBuilder();
    vesselsByCliqueSize.forEach((size, vessels) ->
      cliqueSizes
        .append(cliqueSizes.isEmpty() ? "" : " ")
        .append(size)
        .append(':')
        .append(vessels)
    );
    final double days =
      Duration.between(startDateTime, endDateTime).toSeconds() / 86400.0;
    final StringBuilder byGear = new StringBuilder();
    gearSummaries.forEach((code, gearSummary) ->
      byGear
        .append(byGear.isEmpty() ? "" : " | ")
        .append(code)
        .append(' ')
        .append(gearSummary.format(days))
    );
    simulation.finish();
    return "%s,%s,%d,%d,%.1f,%.1f,%.1f,%d,%.0f,%.2f,%s,%.0f,%s".formatted(
      startDateTime,
      endDateTime,
      trips,
      fishingEvents,
      grossCatchInKg / 1000,
      retainedCatchInKg / 1000,
      fishingEvents == 0 ? 0.0 : distanceFromPortInKm / fishingEvents,
      fishedCells.size(),
      endedTrips == 0 ? 0.0 : tripProfit / endedTrips,
      fishingEvents == 0 ? 0.0 : (double) sharedFishingEvents / fishingEvents,
      cliqueSizes,
      (System.nanoTime() - startNanos) / 1e9,
      byGear
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
