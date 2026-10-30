plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":kairos-core"))
}
