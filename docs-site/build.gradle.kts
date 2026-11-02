plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kairos)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":kairos-core"))
    implementation(project(":kairos-htmx"))
}

application { mainClass.set("docsite.DocsKt") }

val rendered = layout.buildDirectory.dir("rendered")
val site = layout.buildDirectory.dir("site")

// The docs site is a dogfood: its pages are rendered by Kairos, its styles are compiled by Kairos.
tasks.named<JavaExec>("run") {
    args(rootProject.projectDir.absolutePath, rendered.get().asFile.absolutePath)
    outputs.dir(rendered)
}

val assembleSite = tasks.register<Sync>("assembleSite") {
    group = "build"
    description = "Renders the documentation site into docs-site/build/site."
    dependsOn("run")
    into(site)
    from(rendered)
    from(layout.buildDirectory.dir("kairos/jvm/main")) { include("kairos.css", "kairos.map.json") }
}
