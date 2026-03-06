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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotalAllowableCatchQuotasTest {

    private static final CatchCategory CATCH_CATEGORY = CatchCategory.UNCATEGORISED;

    @Test
    void closesFisheryWhenQuotaIsReachedForExactSpecies() {
        // Verifies exact-species catch closes the fishery once quota is reached.
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
        // Verifies sub-quota catch leaves the interval open.
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
        // Verifies a generic species quota counts matching stage-specific catch.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species hakeAllStages = new Species("HKE", null, null);
        final Species hakeJuvenile = new Species("HKE", "juvenile", null);
        final LocalDateTime start = LocalDateTime.of(2026, 3, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, hakeAllStages, 50.0);
        tac.receive(sale(start.plusDays(2), hakeJuvenile, 50.0));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void quotaDoesNotCountSpeciesWithDifferentCode() {
        // Verifies quota accounting ignores catches from a different species code.
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
        // Verifies landed but unsold biomass still counts against TAC.
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
        // Verifies sold and unsold biomass from one sale can jointly exhaust quota.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);
        tac.receive(sale(start.plusDays(1), cod, 60.0, 40.0));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void consecutiveSalesAccumulateUntilLaterSaleClosesInterval() {
        // Verifies multiple sales accumulate and closure happens only on threshold reach.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);

        tac.receive(sale(start.plusDays(1), cod, 40.0));
        assertThat(tac.isPermitted(action(interval))).isTrue();

        tac.receive(sale(start.plusDays(2), cod, 59.9));
        assertThat(tac.isPermitted(action(interval))).isTrue();

        tac.receive(sale(start.plusDays(3), cod, 0.1));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void soldAndUnsoldBiomassAccumulateAcrossMultipleSales() {
        // Verifies sold and unsold biomass from separate sales accumulate toward the same quota.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 7, 15, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);

        tac.receive(sale(start.plusDays(1), cod, 40.0, 10.0));
        assertThat(tac.isPermitted(action(interval))).isTrue();

        tac.receive(sale(start.plusDays(2), cod, 20.0));
        assertThat(tac.isPermitted(action(interval))).isTrue();

        tac.receive(saleWithUnsold(start.plusDays(3), cod, 30.0));
        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void fisheryRemainsOpenBeforeExhaustionTimestampAndClosesFromThatTimestampOnward() {
        // Verifies effective closure starts at exhaustion time rather than interval start.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        final LocalDateTime closureTime = start.plusDays(10);
        final LocalDateTime end = start.plusDays(31);
        final Interval quotaInterval = interval(start, end);
        final Interval beforeClosure = interval(start, closureTime);
        final Interval fromClosureOnward = interval(closureTime, end);

        tac.setQuota(quotaInterval, cod, 100.0);
        tac.receive(sale(closureTime, cod, 100.0));

        assertThat(tac.isPermitted(action(beforeClosure))).isTrue();
        assertThat(tac.isPermitted(action(fromClosureOnward))).isFalse();
    }

    @Test
    void exhaustingEitherSpeciesQuotaClosesSharedInterval() {
        // Verifies exhausting any quota-species closes the shared interval fishery-wide.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final Species haddock = new Species("HAD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);
        tac.setQuota(interval, haddock, 50.0);

        tac.receive(sale(start.plusDays(1), cod, 99.9));
        assertThat(tac.isPermitted(action(interval))).isTrue();

        tac.receive(sale(start.plusDays(2), haddock, 50.0));

        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    @Test
    void nonTargetSpeciesSalesDoNotAffectAnotherSpeciesQuota() {
        // Verifies catch only advances quotas for matching species definitions.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final Species haddock = new Species("HAD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 150.0);
        tac.setQuota(interval, haddock, 50.0);

        tac.receive(sale(start.plusDays(1), cod, 60.0));
        tac.receive(sale(start.plusDays(2), cod, 40.0));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void stageSpecificQuotaDoesNotCountOtherStagesWhenNotCovered() {
        // Verifies a stage-specific quota ignores other stages of the same species code.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species hakeJuvenile = new Species("HKE", "juvenile", null);
        final Species hakeAdult = new Species("HKE", "adult", null);
        final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, hakeJuvenile, 50.0);
        tac.receive(sale(start.plusDays(1), hakeAdult, 50.0));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void rejectsOverlappingQuotaSpeciesWithinOneInterval() {
        // Verifies ambiguous overlapping quota scopes are rejected at definition time.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species hakeAllStages = new Species("HKE", null, null);
        final Species hakeJuvenile = new Species("HKE", "juvenile", null);
        final LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, hakeAllStages, 100.0);

        assertThatThrownBy(() -> tac.setQuota(interval, hakeJuvenile, 50.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot define quota for species 'HKE (juvenile)'")
            .hasMessageContaining("overlapping quota species 'HKE'");
    }

    @Test
    void rejectsDuplicateQuotaDefinitionForSameIntervalAndSpecies() {
        // Verifies the same interval/species quota cannot be defined twice.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 11, 1, 0, 0);
        final LocalDateTime end = start.plusDays(30);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);

        assertThatThrownBy(() -> tac.setQuota(interval, cod, 120.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Quota already defined")
            .hasMessageContaining("species 'COD'");
    }

    @Test
    void salesOutsideConfiguredIntervalDoNotAffectClosure() {
        // Verifies only sales inside the quota interval contribute to closure.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2026, 12, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);
        tac.receive(sale(start.minusSeconds(1), cod, 100.0));
        tac.receive(sale(end, cod, 100.0));

        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    @Test
    void intervalContainsStartButNotEndTimestamp() {
        // Verifies TAC interval containment is start-inclusive and end-exclusive.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2027, 1, 1, 0, 0);
        final LocalDateTime end = start.plusDays(31);
        final Interval interval = interval(start, end);

        tac.setQuota(interval, cod, 100.0);

        tac.receive(sale(start, cod, 100.0));
        assertThat(tac.isPermitted(action(interval))).isFalse();

        final TotalAllowableCatchQuotas tacEndingBoundary = new TotalAllowableCatchQuotas();
        tacEndingBoundary.setQuota(interval, cod, 100.0);
        tacEndingBoundary.receive(sale(end, cod, 100.0));

        assertThat(tacEndingBoundary.isPermitted(action(interval))).isTrue();
    }

    @Test
    void saleInOverlapCountsAgainstEveryMatchingInterval() {
        // Verifies one sale is charged to every overlapping quota interval that contains it.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2027, 2, 1, 0, 0);
        final Interval earlyInterval = interval(start, start.plusDays(20));
        final Interval lateInterval = interval(start.plusDays(10), start.plusDays(31));

        tac.setQuota(earlyInterval, cod, 100.0);
        tac.setQuota(lateInterval, cod, 100.0);
        tac.receive(sale(start.plusDays(15), cod, 100.0));

        assertThat(tac.isPermitted(action(earlyInterval))).isFalse();
        assertThat(tac.isPermitted(action(lateInterval))).isFalse();
    }

    @Test
    void fishingActionOverlappingClosedIntervalIsRejected() {
        // Verifies actions overlapping an effective closure interval are not permitted.
        final TotalAllowableCatchQuotas tac = new TotalAllowableCatchQuotas();
        final Species cod = new Species("COD", null, null);
        final LocalDateTime start = LocalDateTime.of(2027, 3, 1, 0, 0);
        final Interval closedInterval = interval(start, start.plusDays(10));
        final Interval overlappingActionInterval = interval(start.plusDays(9), start.plusDays(20));

        tac.setQuota(closedInterval, cod, 100.0);
        tac.receive(sale(start.plusDays(1), cod, 100.0));

        assertThat(tac.isPermitted(action(overlappingActionInterval))).isFalse();
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
