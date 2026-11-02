plugins {
    kotlin("jvm") version "2.2.20"
    id("dev.kairos") version "0.1.0"
    `maven-publish`
}

group = "dev.kairos.examples"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    implementation("dev.kairos:kairos-core:0.1.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
