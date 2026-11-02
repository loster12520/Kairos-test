plugins {
    kotlin("jvm") version "2.2.20"
    id("dev.kairos") version "0.1.0"
    application
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    implementation("dev.kairos:kairos-core:0.1.0")
    implementation("dev.kairos:kairos-htmx:0.1.0")
}

application {
    mainClass.set("consumer.MainKt")
}

// The plugin writes the stylesheet next to the compilation output.
val compiledStyles = layout.buildDirectory.dir("kairos/jvm/main")
val rendered = layout.buildDirectory.dir("rendered")
val site = layout.buildDirectory.dir("site")

val render by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Renders the consumer page into build/rendered."
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("consumer.MainKt")
    args(rendered.get().asFile.absolutePath)
}

val siteTask = tasks.register<Sync>("site") {
    group = "build"
    description = "Assembles the deployable directory from rendered html and compiled css."
    dependsOn(render)
    into(site)
    from(rendered)
    from(compiledStyles) { include("kairos.css", "kairos.map.json") }
}
