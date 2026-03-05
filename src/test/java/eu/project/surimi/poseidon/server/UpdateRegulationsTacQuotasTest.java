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
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

class UpdateRegulationsTacQuotasTest extends ServiceTest {

    private static final CatchCategory CATCH_CATEGORY = CatchCategory.UNCATEGORISED;
    private static final Species COD = new Species("COD", null, null);
    private static final LocalDateTime START = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime END = START.plusDays(31);
    private static final Interval INTERVAL = Interval.of(START.toInstant(UTC), END.toInstant(UTC));
    private static final double COD_QUOTA = 100.0;
    /*
     * TODO(next session):
     * 1. Add test: updating an existing interval with a lower quota after catches already accrued
     *    should close immediately if catch >= new quota.
     * 2. Decide and test behavior for raising quota on an existing interval after closure:
     *    should interval remain closed, or should it re-open if catch < new quota?
     * 3. Add test for cumulative updates across multiple updateRegulations calls on same interval/species.
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
        // Start a minimal simulation that only includes the TAC regulation component.
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);

        // Send a regulation update defining a COD quota for the requested time window.
        updateCodQuota(simulationId, COD_QUOTA);

        // Emit a matching sale and verify that the TAC component closes the interval.
        simulationManager.getSimulation(simulationId).getEventManager().broadcast(
            sale(START.plusDays(1), COD, COD_QUOTA)
        );

        assertThat(tac.isPermitted(action(INTERVAL))).isFalse();
    }

    @Test
    void updateRegulationsDoesNotCloseWhenSaleIsBelowQuota() {
        final String simulationId = initialiseSimulation();
        final TotalAllowableCatchQuotas tac = getTac(simulationId);
        updateCodQuota(simulationId, COD_QUOTA);
        simulationManager.getSimulation(simulationId).getEventManager().broadcast(
            sale(START.plusDays(1), COD, 99.9)
        );

        assertThat(tac.isPermitted(action(INTERVAL))).isTrue();
    }

    private void updateCodQuota(
        final String simulationId,
        final double quotaInKg
    ) {
        RegulationsConsumerServiceGrpc.newBlockingStub(channel).updateRegulations(
            updateCodQuotaRequest(simulationId, quotaInKg)
        );
    }

    private static UpdateRegulationsRequest updateCodQuotaRequest(
        final String simulationId,
        final double quotaInKg
    ) {
        return UpdateRegulationsRequest.newBuilder()
            .setSimulationId(simulationId)
            .setStartDateTime(toTimestamp(START))
            .setEndDateTime(toTimestamp(END))
            .setRegulationsSummary(
                RegulationsSummary.newBuilder()
                    .addTotalAllowableCatches(
                        TotalAllowableCatch.newBuilder()
                            .setSpecies(
                                build.buf.gen.surimi.v1.Species.newBuilder()
                                    .setSpeciesCode(COD.getCode())
                                    .build()
                            )
                            .setCatch(quotaInKg)
                            .build()
                    )
                    .build()
            )
            .build();
    }

    private TotalAllowableCatchQuotas getTac(final String simulationId) {
        return simulationManager
            .getSimulation(simulationId)
            .getComponent(TotalAllowableCatchQuotas.class);
    }
}
