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
    implementation("POSEIDON:calibration")
    implementation(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.caffeine)
    implementation(libs.grpc.services)
    implementation(libs.jcommander)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.commons.beanutils)
    implementation(libs.commons.io)
    implementation(libs.bundles.opentelemetry)
    implementation(libs.protovalidate)
    compileOnly("${libs.spotbugs.annotations.get()}:${spotbugs.toolVersion.get()}")
    implementation("build.buf.gen:surimi_surimi-protocol_grpc_java:1.82.1.1.20260629150349.449222ce0083")
    testImplementation(libs.jqwik)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.14.3")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "eu.project.surimi.poseidon.server.Server"
}

val writeNorthwesternMedScenario = tasks.register("writeNorthwesternMedScenario", JavaExec::class) {
    dependsOn("classes")
    mainClass.set("uk.ac.ox.poseidon.io.ScenarioWriter")
    classpath = sourceSets["main"].runtimeClasspath
    args(
        "-c", "eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario",
        "-s", "inputs/northwestern_med/scenario.yaml"
    )
}

val profileWesternMedScenario = tasks.register("profileNorthwesternMedScenario", JavaExec::class) {
    dependsOn("classes")
    mainClass.set("eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-XX:StartFlightRecording=duration=120s,filename=build/profile.jfr,settings=profile")
}

val stageForImage = tasks.register<Sync>("stageForImage") {
    val imageDir = layout.buildDirectory.dir("image")
    into(imageDir)
    dependsOn(tasks.named("jar"), writeNorthwesternMedScenario)
    from(tasks.named<Jar>("jar"))
    from(configurations.runtimeClasspath) { into("lib") }
    from("logging.properties")
    from("inputs/northwestern_med/") { into("inputs/northwestern_med/") }
}

val buildDockerImage = tasks.register("buildDockerImage", Exec::class) {
    dependsOn(stageForImage)
    commandLine("docker", "build", "-t", "ghcr.io/official-ewe/surimiposeidon:latest", ".")
}

tasks.register("pushDockerImage", Exec::class) {
    dependsOn(buildDockerImage)
    commandLine("docker", "push", "ghcr.io/official-ewe/surimiposeidon:latest")
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
