/*
 * POSEIDON: an agent-based model of fisheries
 * Copyright (c) 2025, University of Oxford.
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import uk.ac.ox.poseidon.core.Simulation;

import javax.measure.Unit;
import javax.measure.quantity.Mass;
import java.time.Period;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.Status.*;
import static tech.units.indriya.quantity.Quantities.getQuantity;
import static tech.units.indriya.unit.Units.KILOGRAM;

public class SimulationManager {
    private final Cache<UUID, Simulation> simulations = CacheBuilder.newBuilder().build();
    private final Cache<Simulation, SimulationProperties> simulationProperties =
        CacheBuilder.newBuilder().weakKeys().build();

    public static UUID parseId(final String id) {
        try {
            return UUID.fromString(checkNotNull(id));
        } catch (final IllegalArgumentException e) {
            throw INVALID_ARGUMENT
                .withDescription("Invalid UUID: " + id)
                .asRuntimeException();
        }
    }

    public boolean contains(final String simulationId) {
        return contains(parseId(simulationId));
    }

    public boolean contains(final UUID simulationId) {
        return simulations.asMap().containsKey(simulationId);
    }

    public Simulation getSimulation(final String simulationId) {
        return getSimulation(parseId(simulationId));
    }

    public Simulation getSimulation(final UUID simulationId) {
        final Simulation simulation = simulations.getIfPresent(simulationId);
        if (simulation == null) {
            throw NOT_FOUND
                .withDescription("Simulation not found: " + simulationId)
                .asRuntimeException();
        }
        return simulation;
    }

    public SimulationProperties getSimulationProperties(final Simulation simulation) {
        final SimulationProperties properties = simulationProperties.getIfPresent(simulation);
        if (properties == null) throw INTERNAL
            .withDescription("Unable to get simulation properties.")
            .asRuntimeException();
        return properties;
    }

    public void put(
        final UUID simulationId,
        final Simulation simulation,
        final SimulationProperties properties
    ) {
        simulations.put(simulationId, simulation);
        simulationProperties.put(simulation, properties);
    }

    @Data
    public static class SimulationProperties {

        private final Period stepSize;
        private final Unit<Mass> standardMassUnit;
        @Getter(AccessLevel.NONE) private final boolean massUnitIsKg;

        public SimulationProperties(
            final Period stepSize,
            final Unit<Mass> standardMassUnit
        ) {
            this.stepSize = stepSize;
            this.standardMassUnit = standardMassUnit;
            this.massUnitIsKg = standardMassUnit.isEquivalentTo(KILOGRAM);
        }

        public double convertKgToStandardMassUnit(final double valueInKg) {
            return massUnitIsKg
                ? valueInKg
                : getQuantity(valueInKg, standardMassUnit)
                    .getValue()
                    .doubleValue();
        }

        public double convertMassInStandardUnitToKg(final double mass) {
            return massUnitIsKg
                ? mass
                : getQuantity(mass, standardMassUnit).to(KILOGRAM).getValue().doubleValue();
        }
    }

    public void remove(final String simulationId) {
        remove(parseId(simulationId));
    }

    public void remove(final UUID simulationId) {
        simulations.invalidate(simulationId);
    }

}
