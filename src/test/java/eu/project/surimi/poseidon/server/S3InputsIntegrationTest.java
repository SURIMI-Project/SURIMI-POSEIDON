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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Downloads the inputs from the real SURIMI bucket on EDITO, to catch what a mock S3 server
 * can't, such as an SDK upgrade that EDITO's MinIO rejects. Runs only when
 * {@code AWS_ACCESS_KEY_ID} is set (CI sets it from a repository secret), with the keys from the
 * environment; the Vault route is not covered. The bucket, endpoint and region default to
 * SURIMI's and can be overridden with the usual variables.
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
class S3InputsIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void downloadsInputsFromSurimiBucket() {
        final Map<String, String> env = new HashMap<>(Map.of(
            "AWS_BUCKET_NAME", "project-surimi",
            "AWS_S3_ENDPOINT", "minio.dive.edito.eu",
            "AWS_DEFAULT_REGION", "waw3-1"
        ));
        env.putAll(System.getenv());
        final Path inputs = tempDir.resolve("inputs");

        S3Inputs.fromEnvironment(env, inputs).orElseThrow().download();

        assertThat(inputs.resolve("northwestern_med/species.csv")).isNotEmptyFile();
    }
}
