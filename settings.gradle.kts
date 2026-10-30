pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kairos"

include(":kairos-core")
include(":kairos-browser")
include(":kairos-htmx")
include(":kairos-compiler-plugin")
include(":kairos-cli")
include(":demo-web")
include(":demo-web-feature")
include(":blog-backend")
include(":blog-frontend")
