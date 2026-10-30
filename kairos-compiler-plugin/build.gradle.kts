plugins {
    kotlin("jvm") version "2.2.20"
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
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
    // Single source of truth for the class-name algorithm: the very same code the runtime
    // uses, so plugin and runtime can never drift.
    implementation(project(":kairos-core"))
    sharedRuntime(project(":kairos-core"))
}

// The plugin jar is loaded by the compiler without a classpath, so the shared runtime
// classes must be packaged inside it.
tasks.named<Jar>("jar") {
    val resolved = sharedRuntime
    from({ resolved.resolve().filter { it.extension == "jar" }.map { zipTree(it) } }) {
        exclude("META-INF/**")
        exclude("**/*.kotlin_module")
        exclude("**/*.kotlin_metadata")
    }
}
