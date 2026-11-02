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
    implementation("dev.kairos.examples:component-library:0.1.0")
}

application {
    mainClass.set("consumerlib.MainKt")
}

val rendered = layout.buildDirectory.dir("rendered")
val site = layout.buildDirectory.dir("site")

val render = tasks.register<JavaExec>("render") {
    group = "build"
    description = "Renders the page that embeds a component from the binary library."
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("consumerlib.MainKt")
    args(rendered.get().asFile.absolutePath)
}

val siteTask = tasks.register<Sync>("site") {
    group = "build"
    description = "Assembles the page with the merged local + dependency stylesheet."
    dependsOn(render, "kairosMergeStyles")
    into(site)
    from(rendered)
    from(layout.buildDirectory.dir("kairos/merged")) { include("kairos.css", "kairos.map.json") }
}
