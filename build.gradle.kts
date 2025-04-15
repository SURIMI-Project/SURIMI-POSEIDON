plugins {
    application
    alias(libs.plugins.protobuf)
    alias(libs.plugins.shadow)
}

val mockitoAgent: Configuration = configurations.create("mockitoAgent")

dependencies {
    implementation("POSEIDON:regulations")
    implementation("POSEIDON:biology")
    implementation("POSEIDON:io")
    runtimeOnly("POSEIDON:examples")
    implementation(libs.protobuf.java.util)
    implementation(libs.grpc.services)
    implementation(libs.jcommander)
    implementation(libs.bundles.grpc)
    implementation(libs.commons.beanutils)
    implementation(libs.bundles.opentelemetry)
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
    mainClass = "eu.project.surimi.poseidon.App"
}

tasks.shadowJar {
    mergeServiceFiles {
        // those exclusions prevent GeoTools from trying to load the CLib plugin, which crashes:
        exclude("com/sun/media/imageioimpl/plugins/jpeg/CLib*")
        exclude("META-INF/services/javax.imageio.spi.*")
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
