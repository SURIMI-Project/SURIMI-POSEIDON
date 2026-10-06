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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Downloads the scenario inputs from S3 when POSEIDON runs inside SURIMI on EDITO, where the
 * deployment sets {@code AWS_BUCKET_NAME}. Without that variable, POSEIDON reads the local
 * {@code inputs} folder as usual.
 * <p>
 * Everything under {@value #PREFIX} in the bucket is downloaded once, at startup, into the inputs
 * folder, which must not exist yet: nothing local is ever overwritten or deleted. When
 * {@code VAULT_ADDR} is set, the S3 keys come from the Vault secret the deployment points to with
 * the {@code VAULT_*} variables: long-lived keys made in the MinIO console, as the other SURIMI
 * services use. EDITO also sets {@code AWS_ACCESS_KEY_ID} and friends, but those keys expire
 * after 24 hours, so they are ignored then. Without Vault (local tests, CI), the keys come from
 * {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY} and the optional
 * {@code AWS_SESSION_TOKEN}. Any failure stops startup; stale inputs must not be used silently.
 * <p>
 * The service stays up across many experiments, so {@link #warnIfChanged()} is called at each
 * simulation initialisation to warn when the bucket no longer matches what was downloaded.
 */
public final class S3Inputs {

    static final String PREFIX = "surimi-poseidon/";

    private static final System.Logger logger = System.getLogger(S3Inputs.class.getName());
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    // Short, so a hanging S3 can't hold up a simulation initialisation
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);

    private final S3Client s3;
    private final String bucket;
    private final Path inputsFolder;
    // Key -> ETag of each object downloaded; the ETag changes whenever the content does
    private Map<String, String> downloaded = Map.of();

    private S3Inputs(final S3Client s3, final String bucket, final Path inputsFolder) {
        this.s3 = s3;
        this.bucket = bucket;
        this.inputsFolder = inputsFolder;
    }

    /**
     * @return empty if {@code AWS_BUCKET_NAME} is not set in {@code env}, otherwise an
     * {@code S3Inputs} ready to {@link #download()} into {@code inputsFolder}.
     * @throws IllegalStateException if {@code inputsFolder} already exists, a required variable
     *                               is missing, or the keys can't be read from Vault.
     */
    public static Optional<S3Inputs> fromEnvironment(
        final Map<String, String> env,
        final Path inputsFolder
    ) {
        final String bucket = env.get("AWS_BUCKET_NAME");
        if (bucket == null || bucket.isBlank()) {
            logger.log(INFO, "AWS_BUCKET_NAME is not set: reading the local " + inputsFolder);
            return Optional.empty();
        }
        if (Files.exists(inputsFolder)) {
            throw new IllegalStateException(
                "AWS_BUCKET_NAME is set, so inputs are downloaded from S3, but " +
                    inputsFolder.toAbsolutePath() + " already exists; refusing to overwrite it."
            );
        }
        final S3Client s3 = S3Client
            .builder()
            .endpointOverride(endpointUri(required(env, "AWS_S3_ENDPOINT", "the environment")))
            .region(Region.of(required(env, "AWS_DEFAULT_REGION", "the environment")))
            .credentialsProvider(StaticCredentialsProvider.create(credentials(env)))
            // MinIO serves buckets as paths, not subdomains
            .forcePathStyle(true)
            // Recent SDKs add checksums that some S3-compatible servers reject
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .overrideConfiguration(c -> c.apiCallTimeout(TIMEOUT))
            .build();
        return Optional.of(new S3Inputs(s3, bucket, inputsFolder));
    }

    /**
     * Downloads every object under {@value #PREFIX} into the inputs folder.
     *
     * @throws IllegalStateException if there is nothing to download, a key would land outside
     *                               the inputs folder, or the objects changed during the download
     *                               (e.g. an upload was in progress), which could have mixed old
     *                               and new files.
     * @throws SdkException          if S3 can't be reached or refuses the request.
     */
    public void download() {
        final String source = "s3://" + bucket + "/" + PREFIX;
        logger.log(INFO, "Downloading " + source + " into " + inputsFolder.toAbsolutePath());
        final Map<String, String> listing = list(TIMEOUT);
        if (listing.isEmpty()) {
            throw new IllegalStateException("No inputs found under " + source);
        }
        for (final String key : listing.keySet()) {
            final Path target = targetPath(inputsFolder, key);
            try {
                // Never null: targetPath only returns paths strictly inside the inputs folder
                Files.createDirectories(Objects.requireNonNull(target.getParent()));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            s3.getObject(b -> b.bucket(bucket).key(key), target);
        }
        // Misses a mix that persists across both listings (a large file still uploading
        // while smaller ones are already new); warnIfChanged flags it later. A manifest written
        // last by the upload workflow would close that gap.
        if (!list(TIMEOUT).equals(listing)) {
            throw new IllegalStateException(
                "The inputs in " + source + " changed during the download, probably because " +
                    "they are being uploaded. Restart to download them again."
            );
        }
        downloaded = listing;
        logger.log(INFO, "Downloaded " + listing.size() + " files from " + source);
    }

    /**
     * Logs a warning if the objects under {@value #PREFIX} differ from those downloaded at
     * startup, or if that can't be checked. Never throws: it must not stop a simulation from
     * starting.
     */
    public void warnIfChanged() {
        try {
            if (!list(CHECK_TIMEOUT).equals(downloaded)) {
                logger.log(
                    WARNING,
                    "The inputs in s3://" + bucket + "/" + PREFIX + " have changed since this " +
                        "service started. This simulation uses the old ones; restart the service " +
                        "to use the new ones."
                );
            }
        } catch (final RuntimeException e) {
            logger.log(WARNING, "Could not check whether the inputs in S3 have changed", e);
        }
    }

    private Map<String, String> list(final Duration timeout) {
        return s3
            .listObjectsV2Paginator(b -> b
                .bucket(bucket)
                .prefix(PREFIX)
                .overrideConfiguration(c -> c.apiCallTimeout(timeout)))
            .contents()
            .stream()
            .filter(object -> !object.key().endsWith("/"))
            .collect(toUnmodifiableMap(S3Object::key, S3Object::eTag));
    }

    /**
     * @return where the object {@code key} goes: its path under {@value #PREFIX}, resolved
     * against {@code inputsFolder}.
     * @throws IllegalStateException if that path is outside {@code inputsFolder}.
     */
    static Path targetPath(final Path inputsFolder, final String key) {
        final Path folder = inputsFolder.normalize();
        final Path target = folder.resolve(key.substring(PREFIX.length())).normalize();
        if (!target.startsWith(folder) || target.equals(folder)) {
            throw new IllegalStateException("S3 key outside the inputs folder: " + key);
        }
        return target;
    }

    /**
     * @return {@code endpoint} as a URI, with {@code https://} added if it has no scheme (the
     * deployment passes a bare host name).
     */
    static URI endpointUri(final String endpoint) {
        return URI.create(endpoint.contains("://") ? endpoint : "https://" + endpoint);
    }

    private static AwsCredentials credentials(final Map<String, String> env) {
        final String vaultAddress = env.get("VAULT_ADDR");
        final boolean fromVault = vaultAddress != null && !vaultAddress.isBlank();
        final Map<String, String> keys = fromVault ? readVaultSecret(env) : env;
        final String source = fromVault ? "the Vault secret" : "the environment";
        final String accessKeyId = required(keys, "AWS_ACCESS_KEY_ID", source);
        final String secretAccessKey = required(keys, "AWS_SECRET_ACCESS_KEY", source);
        final String sessionToken = keys.get("AWS_SESSION_TOKEN");
        return sessionToken == null || sessionToken.isBlank()
            ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
    }

    /**
     * Reads the key-value secret the {@code VAULT_*} variables point to (KV version 2). The
     * other SURIMI services read the same secret and copy its entries into environment
     * variables, so its keys are the variable names. Never log its values.
     */
    private static Map<String, String> readVaultSecret(final Map<String, String> env) {
        final URI uri = URI.create(
            required(env, "VAULT_ADDR", "the environment").replaceAll("/+$", "") +
                "/v1/" + required(env, "VAULT_MOUNT", "the environment") +
                "/data/" + required(env, "VAULT_TOP_DIR", "the environment") +
                "/" + required(env, "VAULT_RELATIVE_PATH", "the environment")
        );
        final HttpRequest request = HttpRequest
            .newBuilder(uri)
            .header("X-Vault-Token", required(env, "VAULT_TOKEN", "the environment"))
            .timeout(TIMEOUT)
            .build();
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                    "Vault returned HTTP " + response.statusCode() + " for " + uri
                );
            }
            return parseVaultSecret(response.body());
        } catch (final IOException e) {
            throw new IllegalStateException("Could not read the S3 keys from Vault at " + uri, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading Vault at " + uri, e);
        }
    }

    /**
     * @return the entries of a Vault KV version 2 response, found under {@code data.data}.
     */
    static Map<String, String> parseVaultSecret(final String json) {
        final JsonObject data = JsonParser
            .parseString(json)
            .getAsJsonObject()
            .getAsJsonObject("data")
            .getAsJsonObject("data");
        return data
            .entrySet()
            .stream()
            .collect(toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().getAsString()));
    }

    private static String required(
        final Map<String, String> variables,
        final String name,
        final String source
    ) {
        final String value = variables.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "AWS_BUCKET_NAME is set, so inputs are downloaded from S3, but " + name +
                    " is missing from " + source + "."
            );
        }
        return value;
    }
}
