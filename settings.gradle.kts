pluginManagement {
    repositories {
        // Framework development consumes its own published artifacts (compiler plugin and
        // Gradle plugin) from mavenLocal. CI publishes before it configures the sample apps.
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "kairos"

include(":kairos-core")
include(":kairos-browser")
include(":kairos-htmx")
include(":kairos-compiler-plugin")
include(":kairos-gradle-plugin")
include(":kairos-cli")
include(":kairos-bom")

// The sample applications apply `id("dev.kairos")`, which is resolved from mavenLocal. A
// framework-only publish run must not configure them before the plugin exists on disk.
if (providers.gradleProperty("frameworkOnly").orNull != "true") {
    include(":demo-web")
    include(":demo-web-feature")
    include(":blog-backend")
    include(":docs-site")
    include(":blog-frontend")
}
