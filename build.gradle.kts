plugins {
    application
    jacoco
    alias(libs.plugins.protobuf)
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotbugs)
}

val mockitoAgent: Configuration = configurations.create("mockitoAgent")

dependencies {
    implementation("POSEIDON:regulations")
    implementation("POSEIDON:biology")
    implementation("POSEIDON:io")
    runtimeOnly("POSEIDON:examples")
    implementation(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.protobuf.java.util)
    implementation(libs.grpc.services)
    implementation(libs.jcommander)
    implementation(libs.bundles.grpc)
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
    mainClass = "eu.project.surimi.poseidon.Server"
}

tasks.shadowJar {
    mergeServiceFiles {
        // those exclusions prevent GeoTools from trying to load the CLib plugin, which crashes:
        exclude("com/sun/media/imageioimpl/plugins/jpeg/CLib*")
        exclude("META-INF/services/javax.imageio.spi.*")
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
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

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.protocGenGrpcJava.get().toString()
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
            }
        }
    }
}

spotbugs {
    excludeFilter.set(file("spotbugs_exclude.xml"))
}