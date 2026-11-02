plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The Kotlin Gradle plugin API is provided by the build that applies this plugin.
    compileOnly(libs.kotlin.gradle.plugin.api)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("kairos") {
            id = "dev.kairos"
            implementationClass = "dev.kairos.gradle.KairosGradlePlugin"
            displayName = "Kairos"
            description = "Compile-time style folding and layering for Kotlin UI code"
        }
    }
}

// The compiler plugin coordinate is resolved by the Kotlin Gradle plugin at configuration time,
// so the version embedded in this jar has to match the published project version exactly.
tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filesMatching("dev/kairos/gradle/version.properties") {
        expand("version" to projectVersion)
    }
}

tasks.test {
    useJUnitPlatform()
    // The functional tests consume the same published coordinates a user would.
    dependsOn(
        ":kairos-core:publishToMavenLocal",
        ":kairos-compiler-plugin:publishToMavenLocal",
        ":kairos-gradle-plugin:publishToMavenLocal",
    )
}

// Gradle 9.7 ships kotlin-stdlib 2.4.0 on its own classpath while this build is pinned to
// Kotlin 2.2.20. The plugin only touches Gradle/KGP API compiled against the stdlib, so the
// metadata check is noise; the binary is compatible at runtime.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
}
