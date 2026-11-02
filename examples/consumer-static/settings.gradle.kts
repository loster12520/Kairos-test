pluginManagement {
    repositories {
        // The consumer only uses published coordinates; it must never see the framework sources.
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

rootProject.name = "kairos-consumer-static"
