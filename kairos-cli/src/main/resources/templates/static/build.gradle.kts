plugins {
    kotlin("jvm") version "__KOTLIN__"
    id("dev.kairos") version "__KAIROS__"
    application
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

dependencies {
    implementation("dev.kairos:kairos-core:__KAIROS__")
    implementation("dev.kairos:kairos-htmx:__KAIROS__")
}

application { mainClass.set("starter.MainKt") }

val rendered = layout.buildDirectory.dir("rendered")
val site = layout.buildDirectory.dir("site")

// `run` renders the page; declaring the directory keeps Gradle from treating it as stale output.
tasks.named<JavaExec>("run") {
    args(rendered.get().asFile.absolutePath)
    outputs.dir(rendered)
}

// Copies the rendered page and the compiler produced stylesheet into one deployable directory.
val assembleSite = tasks.register<Sync>("assembleSite") {
    group = "build"
    dependsOn("run")
    into(site)
    from(rendered)
    from(layout.buildDirectory.dir("kairos/jvm/main")) { include("kairos.css", "kairos.map.json") }
}
