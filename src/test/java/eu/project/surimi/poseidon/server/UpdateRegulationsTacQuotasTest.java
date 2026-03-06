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

package eu.project.surimi.poseidon.server;

import build.buf.gen.surimi.v1.RegulationsConsumerServiceGrpc;
import build.buf.gen.surimi.v1.RegulationsSummary;
import build.buf.gen.surimi.v1.TotalAllowableCatch;
import build.buf.gen.surimi.v1.UpdateRegulationsRequest;
import eu.project.surimi.poseidon.regulations.TotalAllowableCatchQuotas;
import eu.project.surimi.poseidon.scenarios.TacOnlyScenario;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.threeten.extra.Interval;
import uk.ac.ox.poseidon.agents.catches.CatchCategory;
import uk.ac.ox.poseidon.agents.catches.CategorisedCatch;
import uk.ac.ox.poseidon.agents.market.Sale;
import uk.ac.ox.poseidon.agents.regulations.TemporalFishingAction;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.biology.biomass.Biomass;
import uk.ac.ox.poseidon.biology.species.Species;

import java.time.LocalDateTime;
import java.util.List;

import static eu.project.surimi.poseidon.server.Server.toTimestamp;
import static eu.project.surimi.poseidon.server.mappers.SpeciesMapper.toProtoSpecies;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class UpdateRegulationsTacQuotasTest extends ServiceTest {

    private static final CatchCategory CATCH_CATEGORY = CatchCategory.UNCATEGORISED;
    private static final Species COD = new Species("COD", null, null);
    private static final Species HADDOCK = new Species("HAD", null, null);
    private static final Species HAKE_JUVENILE = new Species("HKE", "juvenile", null);
    private static final LocalDateTime START = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime END = START.plusDays(31);
    private static final Interval INTERVAL = Interval.of(START.toInstant(UTC), END.toInstant(UTC));
    private static final double COD_QUOTA = 100.0;
    /*
     * TODO(test plan):
     * 1. Request rejection through gRPC
     *    - duplicate quota definitions for the same interval/species should be rejected.
     *    - overlapping quota-species definitions in one interval should be rejected.
     * 2. Interval scoping through requests
     *    - requests for different, non-overlapping intervals should not interfere with each other.
     *    - overlapping requested intervals should remain independent except for shared sale accounting
     *      in the TAC component itself.
     */

    UpdateRegulationsTacQuotasTest() {
        super(TacOnlyScenario.class);
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

    @Test
    void updateRegulationsAppliesTacQuotasToSimulation() {
        // Verifies the API wires a TAC quota into the simulation and closes on matching catch.
        // Start a minimal simulation that only includes the TAC regulation component.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        // Send a regulation update defining a COD quota for the requested time window.
        updateQuota(simulationId, COD, COD_QUOTA);

        // Emit a matching sale and verify that the TAC component closes the interval.
        broadcastSale(simulationId, START.plusDays(1), COD, COD_QUOTA);

        assertIntervalClosed(tac, INTERVAL);
    }

    @Test
    void updateRegulationsAppliesMultipleTacEntriesFromOneRequest() {
        // Verifies one gRPC request can install multiple TAC species quotas in the same interval.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        updateQuotas(
            simulationId,
            new QuotaEntry(COD, 100.0),
            new QuotaEntry(HADDOCK, 50.0)
        );

        broadcastSale(simulationId, START.plusDays(1), COD, 99.9);
        assertIntervalOpen(tac, INTERVAL);

        broadcastSale(simulationId, START.plusDays(2), HADDOCK, 50.0);
        assertIntervalClosed(tac, INTERVAL);
    }

    @Test
    void updateRegulationsMapsLifeStageSpeciesFromRequestPayload() {
        // Verifies request species payloads preserve life-stage data when installing TAC quotas.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        updateQuota(simulationId, HAKE_JUVENILE, 50.0);

        broadcastSale(simulationId, START.plusDays(1), HAKE_JUVENILE, 50.0);
        assertIntervalClosed(tac, INTERVAL);
    }

    @Test
    void updateRegulationsRejectsDuplicateQuotaDefinitionForSameIntervalAndSpecies() {
        // Verifies gRPC updates reject re-defining the same quota scope.
        final String simulationId = initialiseSimulation();
        updateQuota(simulationId, COD, 100.0);

        final Throwable thrown = catchThrowable(() ->
            updateQuota(simulationId, COD, 200.0)
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("Quota already defined")
            .contains("species 'COD'");
    }

    @Test
    void updateRegulationsRejectsOverlappingQuotaSpeciesWithinOneInterval() {
        // Verifies gRPC updates reject ambiguous overlapping quota scopes.
        final String simulationId = initialiseSimulation();
        final Species hakeAllStages = new Species("HKE", null, null);
        final Species hakeJuvenile = new Species("HKE", "juvenile", null);

        final Throwable thrown = catchThrowable(() ->
            updateQuotas(
                simulationId,
                new QuotaEntry(hakeAllStages, 100.0),
                new QuotaEntry(hakeJuvenile, 50.0)
            )
        );

        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(((StatusRuntimeException) thrown).getStatus().getDescription())
            .contains("Cannot define quota for species 'HKE (juvenile)'")
            .contains("overlapping quota species 'HKE'");
    }

    @Test
    void updateRegulationsKeepsNonOverlappingIntervalsIndependent() {
        // Verifies separate requests for disjoint intervals do not interfere with each other.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);
        final LocalDateTime laterStart = END.plusDays(1);
        final LocalDateTime laterEnd = laterStart.plusDays(31);
        final Interval laterInterval = Interval.of(laterStart.toInstant(UTC), laterEnd.toInstant(UTC));

        updateQuota(simulationId, START, END, COD, 100.0);
        updateQuota(simulationId, laterStart, laterEnd, COD, 100.0);

        broadcastSale(simulationId, START.plusDays(1), COD, 100.0);

        assertIntervalClosed(tac, INTERVAL);
        assertIntervalOpen(tac, laterInterval);
    }

    @Test
    void updateRegulationsKeepsOverlappingIntervalsIndependentApartFromSharedSaleAccounting() {
        // Verifies overlapping interval requests share sale accounting only where their windows overlap.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);
        final LocalDateTime overlapStart = START.plusDays(10);
        final LocalDateTime overlapEnd = END.plusDays(10);
        final Interval overlapInterval = Interval.of(overlapStart.toInstant(UTC), overlapEnd.toInstant(UTC));

        updateQuota(simulationId, START, END, COD, 100.0);
        updateQuota(simulationId, overlapStart, overlapEnd, COD, 100.0);

        broadcastSale(simulationId, START.plusDays(5), COD, 100.0);

        assertIntervalClosed(tac, INTERVAL);
        assertThat(tac.getEffectiveClosureIntervals())
            .containsExactly(Interval.of(START.plusDays(5).toInstant(UTC), END.toInstant(UTC)));

        broadcastSale(simulationId, overlapStart.plusDays(1), COD, 100.0);

        assertThat(tac.getEffectiveClosureIntervals())
            .containsExactly(
                Interval.of(START.plusDays(5).toInstant(UTC), END.toInstant(UTC)),
                Interval.of(overlapStart.plusDays(1).toInstant(UTC), overlapEnd.toInstant(UTC))
            );
        assertIntervalClosed(tac, overlapInterval);
    }

    private void updateQuota(
        final String simulationId,
        final Species species,
        final double quotaInKg
    ) {
        updateQuota(simulationId, START, END, species, quotaInKg);
    }

    private void updateQuota(
        final String simulationId,
        final LocalDateTime start,
        final LocalDateTime end,
        final Species species,
        final double quotaInKg
    ) {
        updateQuotas(
            simulationId,
            start,
            end,
            new QuotaEntry(species, quotaInKg)
        );
    }

    private void updateQuotas(
        final String simulationId,
        final QuotaEntry... quotaEntries
    ) {
        RegulationsConsumerServiceGrpc.newBlockingStub(channel).updateRegulations(
            updateQuotaRequest(simulationId, START, END, quotaEntries)
        );
    }

    private void updateQuotas(
        final String simulationId,
        final LocalDateTime start,
        final LocalDateTime end,
        final QuotaEntry... quotaEntries
    ) {
        RegulationsConsumerServiceGrpc.newBlockingStub(channel).updateRegulations(
            updateQuotaRequest(simulationId, start, end, quotaEntries)
        );
    }

    private void broadcastSale(
        final String simulationId,
        final LocalDateTime dateTime,
        final Species species,
        final double biomassInKg
    ) {
        simulationManager.getSimulation(simulationId).getEventManager().broadcast(
            sale(dateTime, species, biomassInKg)
        );
    }

    private static void assertIntervalOpen(
        final TotalAllowableCatchQuotas tac,
        final Interval interval
    ) {
        assertThat(tac.isPermitted(action(interval))).isTrue();
    }

    private static void assertIntervalClosed(
        final TotalAllowableCatchQuotas tac,
        final Interval interval
    ) {
        assertThat(tac.isPermitted(action(interval))).isFalse();
    }

    private static UpdateRegulationsRequest updateQuotaRequest(
        final String simulationId,
        final LocalDateTime start,
        final LocalDateTime end,
        final QuotaEntry... quotaEntries
    ) {
        final var regulationsSummary = RegulationsSummary.newBuilder();
        for (final QuotaEntry quotaEntry : quotaEntries) {
            regulationsSummary.addTotalAllowableCatches(
                TotalAllowableCatch.newBuilder()
                    .setSpecies(toProtoSpecies(quotaEntry.species()))
                    .setCatch(quotaEntry.quotaInKg())
                    .build()
            );
        }
        return UpdateRegulationsRequest.newBuilder()
            .setSimulationId(simulationId)
            .setStartDateTime(toTimestamp(start))
            .setEndDateTime(toTimestamp(end))
            .setRegulationsSummary(regulationsSummary.build())
            .build();
    }

    private TotalAllowableCatchQuotas getTac(final String simulationId) {
        return simulationManager
            .getSimulation(simulationId)
            .getComponent(TotalAllowableCatchQuotas.class);
    }

    private record QuotaEntry(Species species, double quotaInKg) {
    }
}
