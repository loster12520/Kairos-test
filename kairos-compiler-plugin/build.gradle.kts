plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

val sharedRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // Only the runtime's own classes belong in the plugin jar; pulling its transitive
    // dependencies would drag the whole kotlin-stdlib in.
    isTransitive = false
}

dependencies {
    // The compiler is provided by the Kotlin daemon: never bundle it.
    compileOnly(libs.kotlin.compiler.embeddable)
    // Single source of truth for the class-name algorithm: the very same code the runtime
    // uses, so plugin and runtime can never drift.
    implementation(project(":kairos-core"))
    sharedRuntime(project(":kairos-core"))
}

// The plugin jar is loaded by the compiler without a classpath, so the shared runtime
// classes must be packaged inside it. The Kotlin compiler plugin artifact is what the Gradle
// plugin points at, so the jar must be self contained.
tasks.named<Jar>("jar") {
    val resolved = sharedRuntime
    from({ resolved.resolve().filter { it.extension == "jar" }.map { zipTree(it) } }) {
        exclude("META-INF/**")
        exclude("**/*.kotlin_module")
        exclude("**/*.kotlin_metadata")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
