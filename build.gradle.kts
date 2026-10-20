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

buildscript {
    dependencies {
        // node-gradle (latest release, 7.1.0) brings a vulnerable Jackson 2 onto the build
        // classpath; drop once a node-gradle release brings 2.18.11 or later
        classpath(platform(libs.jackson2.bom))
    }
}

plugins {
    application
    jacoco
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.node.gradle)
    alias(libs.plugins.spotless)
    // Adds Lombok to every source set, and makes Javadoc read the delomboked sources so it
    // documents the members Lombok generates
    alias(libs.plugins.freefair.lombok)
}

lombok {
    version = libs.versions.lombok
}

node {
    download = true
    version = libs.versions.nodejs
    // Downloaded through the Node.js repository declared in settings.gradle.kts
    distBaseUrl = null
}

// Prettier versions come from package.json, which IntelliJ's Prettier integration also uses, so
// that formatting in the IDE and in the build always agree
@Suppress("UNCHECKED_CAST")
val npmPackages = (groovy.json.JsonSlurper().parse(file("package.json")) as Map<String, Any>)
    .getValue("devDependencies") as Map<String, String>
val isWindows = System.getProperty("os.name").startsWith("Windows")

spotless {
    java {
        removeUnusedImports()
        prettier(npmPackages)
            .nodeExecutable(
                tasks.nodeSetup.get().nodeDir.get()
                    .file(if (isWindows) "node.exe" else "bin/node").asFile
            )
            .npmExecutable(
                tasks.npmSetup.get().npmDir.get()
                    .file(if (isWindows) "npm.cmd" else "bin/npm").asFile
            )
            .configFile(".prettierrc")
            .npmInstallCache()
        // Existing years are kept as they are; IntelliJ's copyright profile updates them on edit
        licenseHeader(
            """
            /*
             * POSEIDON: an agent-based model of fisheries
             * Copyright (c) ${'$'}YEAR, University of Oxford.
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
            """.trimIndent() + "\n\n"
        )
    }
}

tasks.named("spotlessJava") {
    dependsOn(tasks.nodeSetup, tasks.npmSetup)
}

val mockitoAgent: Configuration = configurations.create("mockitoAgent")

abstract class MockitoAgentArgumentProvider : CommandLineArgumentProvider {
    @get:InputFile
    @get:Classpath
    abstract val agentJar: RegularFileProperty

    override fun asArguments(): Iterable<String> {
        return listOf("-javaagent:${agentJar.asFile.get().absolutePath}", "-Xshare:off")
    }
}

val mockitoAgentArgs = objects.newInstance(MockitoAgentArgumentProvider::class).apply {
    agentJar.set(layout.file(mockitoAgent.elements.map { it.single().asFile }))
}

dependencies {
    implementation("POSEIDON:gui")
    implementation("POSEIDON:regulations")
    implementation("POSEIDON:examples")
    implementation("POSEIDON:fishing")
    implementation("POSEIDON:biology")
    implementation("POSEIDON:io")
    implementation("POSEIDON:calibration")
    implementation(libs.caffeine)
    implementation(libs.grpc.services)
    implementation(libs.jcommander)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.commons.beanutils)
    implementation(libs.commons.io)
    implementation(libs.bundles.opentelemetry)
    implementation(libs.protovalidate)
    implementation(libs.aws.s3) {
        // S3Inputs uses the URL connection client; keep the SDK's other HTTP clients off the classpath
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "apache5-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation(libs.aws.url.connection.client)
    implementation(libs.gson)
    compileOnly("${libs.spotbugs.annotations.get()}:${spotbugs.toolVersion.get()}")
    implementation("build.buf.gen:surimi_surimi-protocol_grpc_java:1.84.0.2.20261006161938.4e6356c78867")
    testImplementation(libs.jqwik)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
    mockitoAgent(libs.mockito) { isTransitive = false }
}

testing {
    suites {
        @Suppress("UnstableApiUsage")
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit.get())
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // MASON makes steppables and the simulation Serializable, but POSEIDON never serializes them
    options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all,-serial"))
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
        "-s", "scenarios/northwestern_med.yaml"
    )
}

val profileNorthwesternMedScenario = tasks.register("profileNorthwesternMedScenario", JavaExec::class) {
    dependsOn("classes")
    mainClass.set("eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-XX:StartFlightRecording=duration=120s,filename=build/profile.jfr,settings=profile")
}

tasks.register("runNorthwesternMedGui", JavaExec::class) {
    dependsOn("classes")
    mainClass.set("eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenarioWithUI")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register("runNorthwesternMedCalibration", JavaExec::class) {
    dependsOn("classes")
    mainClass.set("eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedCalibration")
    classpath = sourceSets["main"].runtimeClasspath
    // The population is evaluated in parallel, about 0.35 to 0.7 GB live per simulation, so the
    // heap scales with the machine's memory rather than being fixed
    jvmArgs("-XX:MaxRAMPercentage=75")
}

val stageForImage =tasks.register<Sync>("stageForImage") {
    val imageDir = layout.buildDirectory.dir("image")
    into(imageDir)
    dependsOn(tasks.named("jar"), writeNorthwesternMedScenario)
    from(tasks.named<Jar>("jar"))
    from(configurations.runtimeClasspath) { into("lib") }
    from("logging.properties")
    from("scenarios/northwestern_med.yaml") { into("scenarios") }
}

val dockerImageName = "ghcr.io/surimi-project/surimiposeidon"
val dockerImageTags = listOf("latest") +
    listOfNotNull(providers.gradleProperty("dockerImageVersion").orNull?.takeIf { it.isNotBlank() })

val buildDockerImage = tasks.register("buildDockerImage", Exec::class) {
    dependsOn(stageForImage)
    commandLine(listOf("docker", "build", ".") + dockerImageTags.flatMap { listOf("-t", "$dockerImageName:$it") })
}

tasks.register("pushDockerImage", Exec::class) {
    dependsOn(buildDockerImage)
    // docker push takes a single image reference, so each tag is pushed separately
    commandLine("sh", "-c", dockerImageTags.joinToString(" && ") { "docker push $dockerImageName:$it" })
}

// Missing comments are not reported, and any other Javadoc problem fails the build
tasks.withType<Javadoc>().configureEach {
    // Delombok copies every file of the source folders, such as .editorconfig, which Javadoc
    // would read as packages
    include("**/*.java")
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Werror", true)
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    maxHeapSize = "4g"
    finalizedBy(tasks.jacocoTestReport)
    jvmArgumentProviders.add(mockitoAgentArgs)
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-Dio.grpc.netty.shaded.io.netty.noUnsafe=true"
    )
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
