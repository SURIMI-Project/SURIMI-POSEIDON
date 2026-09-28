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

/**
 * SURIMI-specific regulations plugged into POSEIDON's regulations engine: TAC quotas
 * ({@link eu.project.surimi.poseidon.regulations.TotalAllowableCatchQuotas}), MPA closures
 * ({@link eu.project.surimi.poseidon.regulations.MpaClosurePredicate}), and port closures
 * ({@link eu.project.surimi.poseidon.regulations.PortClosurePredicate}). The MPA and port closure
 * predicates are built from tables via a {@code *FromTableFactory} that groups rows into an
 * {@code ImmutableMap} keyed by the relevant IDs; see {@link eu.project.surimi.poseidon.regulations.Factories}
 * for the YAML-facing entry points.
 */
package eu.project.surimi.poseidon.regulations;
