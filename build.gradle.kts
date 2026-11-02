import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// Root build: every version lives in gradle/libs.versions.toml, every module gets the same
// coordinates, and publishing is opt-in per module.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.17.0"
}

// Binary compatibility gate: published modules keep an api/*.api dump; a public signature change
// fails `apiCheck` until the dump is regenerated on purpose.
apiValidation {
    // Sample apps are not part of the published API surface. The list is filtered because
    // `-PframeworkOnly=true` builds configure without those projects.
    val samples = setOf("demo-web", "demo-web-feature", "blog-backend", "blog-frontend", "docs-site", "kairos-bom")
    val present = subprojects.map { it.name }.toSet()
    ignoredProjects.addAll(samples.filter { it in present })
}

allprojects {
    group = "dev.kairos"
    version = "0.1.0"
}

subprojects {
    // Every JVM artifact publishes sources; IDE navigation and the "read the framework" promise
    // both depend on it.
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> { withSourcesJar() }
    }
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                // Local consumer verification: ./gradlew publishToMavenLocal is the supported
                // development flow; CI publishes into build/repo for the clean-room check.
                maven {
                    name = "localBuild"
                    url = uri(rootProject.layout.buildDirectory.dir("repo"))
                }
            }
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
                    description.set("Kairos: compiler-driven, HTML-first UI toolkit for Kotlin")
                    url.set("https://github.com/kairos-ui/kairos")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
    }
}
