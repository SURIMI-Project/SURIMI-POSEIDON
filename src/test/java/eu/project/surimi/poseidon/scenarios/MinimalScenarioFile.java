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

package eu.project.surimi.poseidon.scenarios;

import uk.ac.ox.poseidon.io.ScenarioWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A utility class responsible for managing a minimal scenario file for testing purposes.
 * <p>
 * The class ensures that a minimal scenario in YAML format is created and stored in a predefined
 * location. It provides a static method to retrieve the path to the generated scenario file.
 * <p>
 * This class is designed to be used as part of the test initialization process to set up the
 * simulation environment with a minimal configuration.
 * <p>
 * The scenario file is generated automatically upon class loading by leveraging the capabilities of
 * the {@link ScenarioWriter} and {@link MinimalScenario}.
 * <p>
 * Thread-safety: This class is thread-safe as it uses final static fields and immutable behavior.
 * Mutability: Instances of this class cannot be created as it has a private constructor.
 */
public final class MinimalScenarioFile {

    private static final Path path = writeScenarioFile();

    private MinimalScenarioFile() {}

    private static Path writeScenarioFile() {
        final Path scenarioPath = Path.of("build", "minimal_scenario", "scenario.yaml");
        try {
            final Path parent = scenarioPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            new ScenarioWriter().write(new MinimalScenario().get(), scenarioPath);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write minimal scenario", e);
        }
        return scenarioPath;
    }

    public static Path getPath() {
        return MinimalScenarioFile.path;
    }
}
