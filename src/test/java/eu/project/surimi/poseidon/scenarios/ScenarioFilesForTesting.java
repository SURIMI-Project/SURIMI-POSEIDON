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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import uk.ac.ox.poseidon.core.Scenario;
import uk.ac.ox.poseidon.io.ScenarioWriter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class ScenarioFilesForTesting {

    private static final LoadingCache<Class<? extends Supplier<Scenario>>, Path> paths =
        CacheBuilder
            .newBuilder()
            .build(CacheLoader.from(ScenarioFilesForTesting::writeScenarioFile));

    private ScenarioFilesForTesting() {}

    private static Path writeScenarioFile(final Class<? extends Supplier<Scenario>> scenarioClass) {
        final Path scenarioPath = Path.of(
            "build", "tmp", "test", scenarioClass.getSimpleName() + ".yaml"
        );
        try {
            final Path parent = scenarioPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            final Supplier<Scenario> scenario =
                scenarioClass.getDeclaredConstructor().newInstance();
            new ScenarioWriter().write(scenario.get(), scenarioPath);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write minimal scenario", e);
        } catch (
            final InvocationTargetException | InstantiationException |
                  IllegalAccessException | NoSuchMethodException e
        ) {
            throw new RuntimeException("Failed to instantiate scenario", e);
        }
        return scenarioPath;
    }

    public static Path getPath(final Class<? extends Supplier<Scenario>> scenarioClass) {
        return paths.getUnchecked(scenarioClass);
    }
}
