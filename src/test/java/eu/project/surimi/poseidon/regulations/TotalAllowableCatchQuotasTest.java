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
import uk.ac.ox.poseidon.biology.buckets.Bucket;
import uk.ac.ox.poseidon.biology.species.Species;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

class TotalAllowableCatchQuotasTest {

    private static final CatchCategory CATCH_CATEGORY = CatchCategory.UNCATEGORISED;

    @Test
    void closesFisheryWhenQuotaIsReachedForExactSpecies() {
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);
        tac.receive(sale(start.plusDays(1), cod, 100.0));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void staysOpenWhenQuotaIsNotReached() {
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 2, 1, 0, 0);
        final LocalDateTime end = start.plusDays(28);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);
        tac.receive(sale(start.plusDays(1), cod, 99.9));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void quotaWithoutLifeStageCoversLifeStageSpecificCatches() {
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species hakeAllStages = new Species("HKE", null, null);
        final Species hakeJuvenile = new Species("HKE", "JUV", null);
        final LocalDateTime start = LocalDateTime.of(2026, 3, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, hakeAllStages, 50.0);
        tac.receive(sale(start.plusDays(2), hakeJuvenile, 50.0));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void quotaDoesNotCountSpeciesWithDifferentCode() {
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species hake = new Species("HKE", null, null);
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 4, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, hake, 10.0);
        tac.receive(sale(start.plusDays(1), cod, 100.0));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void unsoldCatchCountsTowardsQuota() {
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 20.0);
        tac.receive(saleWithUnsold(start.plusDays(1), cod, 20.0));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void soldAndUnsoldCombinedCanCloseQuota() {
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);
        tac.receive(sale(start.plusDays(1), cod, 60.0, 40.0));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    private static Interval interval(final LocalDateTime start, final LocalDateTime end) {
        return Interval.of(start.toInstant(UTC), end.toInstant(UTC));
    }

    private static Sale sale(
        final LocalDateTime dateTime,
        final Species species,
        final double biomassInKg
    ) {
        return sale(dateTime, species, biomassInKg, 0.0);
    }

    private static Sale sale(
        final LocalDateTime dateTime,
        final Species species,
        final double soldBiomassInKg,
        final double unsoldBiomassInKg
    ) {
        return new Sale(
            dateTime,
            "sale-1",
            null,
            null,
            soldBiomassInKg > 0.0
                ? List.of(new Sale.Item(CATCH_CATEGORY, species, Biomass.ofKg(soldBiomassInKg), null))
                : List.of(),
            unsoldBiomassInKg > 0.0
                ? new CategorisedCatch(Map.of(CATCH_CATEGORY, Bucket.of(species, unsoldBiomassInKg)))
                : CategorisedCatch.empty()
        );
    }

    private static Sale saleWithUnsold(
        final LocalDateTime dateTime,
        final Species species,
        final double unsoldBiomassInKg
    ) {
        return sale(dateTime, species, 0.0, unsoldBiomassInKg);
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
