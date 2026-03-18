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

package eu.project.surimi.poseidon;

import build.buf.gen.surimi.v1.WorkflowServiceGrpc;
import io.grpc.Status;

import java.io.IOException;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class ProtocolVersionExtractor {

    public static String getSurimiProtocolVersion() {
        final String fullString =
            extractVersion(WorkflowServiceGrpc.class).orElseThrow(() ->
                Status.INTERNAL
                    .withDescription("Unable to extract protocol version")
                    .asRuntimeException()
            );
        final String[] a = fullString.split("\\.");
        return a[a.length - 1];
    }

    // Source - https://stackoverflow.com/a/49889511
    // Posted by Sean Patrick Floyd, modified by community. See post 'Timeline' for change history
    // Retrieved 2026-03-18, License - CC BY-SA 3.0

    /**
     * Reads a library's version if the library contains a Maven pom.properties file. You probably
     * want to cache the output or write it to a constant.
     *
     * @param referenceClass any class from the library to check
     * @return an Optional containing the version String, if present
     */
    public static Optional<String> extractVersion(
        final Class<?> referenceClass
    ) {
        return Optional.ofNullable(referenceClass)
            .map(cls -> unthrow(cls::getProtectionDomain))
            .map(ProtectionDomain::getCodeSource)
            .map(CodeSource::getLocation)
            .map(url -> unthrow(url::openStream))
            .map(is -> unthrow(() -> new JarInputStream(is)))
            .map(jis -> readPomProperties(jis, referenceClass))
            .map(props -> props.getProperty("version"));
    }

    /**
     * Locate the pom.properties file in the Jar, if present, and return a Properties object
     * representing the properties in that file.
     *
     * @param jarInputStream the jar stream to read from
     * @param referenceClass the reference class, whose ClassLoader we'll be using
     * @return the Properties object, if present, otherwise null
     */
    private static Properties readPomProperties(
        final JarInputStream jarInputStream,
        final Class<?> referenceClass
    ) {

        try {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                final String entryName = jarEntry.getName();
                if (entryName.startsWith("META-INF")
                    && entryName.endsWith("pom.properties")) {

                    final Properties properties = new Properties();
                    final ClassLoader classLoader = referenceClass.getClassLoader();
                    properties.load(classLoader.getResourceAsStream(entryName));
                    return properties;
                }
            }
        } catch (final IOException ignored) {}
        return null;
    }

    /**
     * Wrap a Callable with code that returns null when an exception occurs, so it can be used in an
     * Optional.map() chain.
     */
    private static <T> T unthrow(final Callable<T> code) {
        try {
            return code.call();
        } catch (final Exception ignored) {return null;}
    }

}
