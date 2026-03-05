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

import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.agents.catches.CatchCategory;
import uk.ac.ox.poseidon.agents.catches.CategorisedCatch;
import uk.ac.ox.poseidon.agents.market.Sale;
import uk.ac.ox.poseidon.agents.regulations.TemporalFishingAction;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.biology.biomass.Biomass;
import uk.ac.ox.poseidon.biology.species.Species;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.core.Simulation;
import uk.ac.ox.poseidon.core.scopes.SimulationScope;

import java.time.LocalDateTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ox.poseidon.core.time.Factories.dateTime;

class TotalAllowableCatchQuotasFactoryTest {

    private static final CatchCategory CATCH_CATEGORY = CatchCategory.UNCATEGORISED;

    @Test
    void registersRegulationAsSaleListener() {
        final Simulation simulation = Scenario.builder()
            .startingDateTime(dateTime(LocalDateTime.of(2026, 1, 1, 0, 0)))
            .build()
            .startNewSimulation();
        final TotalAllowableCatchQuotas tac =
            new TotalAllowableCatchQuotasFactory().get(new SimulationScope(simulation));
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        final Interval interval = Interval.of(
            start.toInstant(UTC),
            start.plusDays(31).toInstant(UTC)
        );

        tac.setQuota(interval, cod, 100.0);
        simulation.getEventManager().broadcast(sale(start.plusDays(1), cod, 100.0));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    private static Sale sale(
        final LocalDateTime dateTime,
        final Species species,
        final double biomassInKg
    ) {
        return new Sale(
            dateTime,
            "sale-1",
            null,
            null,
            List.of(new Sale.Item(CATCH_CATEGORY, species, Biomass.ofKg(biomassInKg), null)),
            CategorisedCatch.empty()
        );
    }

    private static TemporalFishingAction action(final Interval interval) {
        return new TemporalFishingAction() {
            @Override
            public Interval getInterval() {
                return interval;
            }

            @Override
            public Vessel getAgent() {
                return null;
            }
        };
    }
}
