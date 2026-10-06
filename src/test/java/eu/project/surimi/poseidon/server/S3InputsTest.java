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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3InputsTest {

    private static final Map<String, String> S3_ENV = Map.of(
        "AWS_BUCKET_NAME", "project-surimi",
        "AWS_S3_ENDPOINT", "minio.dive.edito.eu",
        "AWS_DEFAULT_REGION", "waw3-1"
    );

    @TempDir
    Path tempDir;

    @Test
    void withoutBucketReadsLocalInputs() {
        assertThat(S3Inputs.fromEnvironment(Map.of(), tempDir.resolve("inputs"))).isEmpty();
        assertThat(S3Inputs.fromEnvironment(Map.of("AWS_BUCKET_NAME", " "), tempDir)).isEmpty();
    }

    @Test
    void refusesExistingInputsFolder() throws IOException {
        final Path inputs = Files.createDirectory(tempDir.resolve("inputs"));
        assertThatThrownBy(() -> S3Inputs.fromEnvironment(S3_ENV, inputs))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void keysInEnvironmentWinOverVault() {
        // No VAULT_* variables: reading Vault would throw
        final Map<String, String> env = with(
            "AWS_ACCESS_KEY_ID", "id",
            "AWS_SECRET_ACCESS_KEY", "secret"
        );
        assertThat(S3Inputs.fromEnvironment(env, tempDir.resolve("inputs"))).isPresent();
    }

    @Test
    void withoutKeysNeedsVault() {
        assertThatThrownBy(() -> S3Inputs.fromEnvironment(S3_ENV, tempDir.resolve("inputs")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("VAULT_ADDR");
    }

    @Test
    void parsesVaultSecret() {
        final String json = """
            {"data": {"data": {"AWS_ACCESS_KEY_ID": "id", "AWS_SECRET_ACCESS_KEY": "secret"},
                      "metadata": {"version": 3}}}
            """;
        assertThat(S3Inputs.parseVaultSecret(json)).isEqualTo(Map.of(
            "AWS_ACCESS_KEY_ID", "id",
            "AWS_SECRET_ACCESS_KEY", "secret"
        ));
    }

    @Test
    void addsSchemeToBareEndpoint() {
        assertThat(S3Inputs.endpointUri("minio.dive.edito.eu"))
            .isEqualTo(URI.create("https://minio.dive.edito.eu"));
        assertThat(S3Inputs.endpointUri("http://localhost:9000"))
            .isEqualTo(URI.create("http://localhost:9000"));
    }

    @Test
    void mapsKeysIntoInputsFolder() {
        final Path inputs = Path.of("inputs");
        assertThat(S3Inputs.targetPath(inputs, "surimi-poseidon/northwestern_med/species.csv"))
            .isEqualTo(Path.of("inputs/northwestern_med/species.csv"));
        assertThatThrownBy(() -> S3Inputs.targetPath(inputs, "surimi-poseidon/../escaped.csv"))
            .isInstanceOf(IllegalStateException.class);
    }

    private static Map<String, String> with(final String... keysAndValues) {
        final var env = new HashMap<>(S3_ENV);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            env.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return env;
    }
}
