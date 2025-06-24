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

plugins {
    application
    jacoco
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotbugs)
}

val mockitoAgent: Configuration = configurations.create("mockitoAgent")

dependencies {
    implementation("POSEIDON:gui")
    implementation("POSEIDON:regulations")
    implementation("POSEIDON:examples")
    implementation("POSEIDON:agents")
    implementation("POSEIDON:biology")
    implementation("POSEIDON:io")
    implementation(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.grpc.services)
    implementation("build.buf.gen:surimi_surimi-protocol_grpc_java:1.73.0.1.20250623211709.a094771b78d7")
    implementation(libs.jcommander)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.commons.beanutils)
    implementation(libs.bundles.opentelemetry)
    compileOnly("${libs.spotbugs.annotations.get()}:${spotbugs.toolVersion.get()}")
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.10.3")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "eu.project.surimi.poseidon.server.Server"
}

tasks.shadowJar {
    mergeServiceFiles {
        // those exclusions prevent GeoTools from trying to load the CLib plugin, which crashes:
        exclude("com/sun/media/imageioimpl/plugins/jpeg/CLib*")
        exclude("META-INF/services/javax.imageio.spi.*")
    }
}

tasks.register("buildDockerImage", Exec::class) {
    dependsOn(tasks.named("shadowJar"))
    commandLine("docker", "build", "-t", "nicolaspayette/poseidon:latest", ".")
}

tasks.register("pushDockerImage", Exec::class) {
    dependsOn(tasks.named("buildDockerImage"))
    commandLine("docker", "push", "nicolaspayette/poseidon:latest")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)  // Ensure tests run before generating the report
    reports {
        xml.required.set(true)  // XML report needed for coverage tools
        html.required.set(true)  // HTML report for easier human viewing
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "METHOD"
            excludes = listOf("lombok.Generated")
        }
    }
}

spotbugs {
    excludeFilter.set(file("spotbugs_exclude.xml"))
}
