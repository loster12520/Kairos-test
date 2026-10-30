plugins {
    kotlin("multiplatform") version "2.2.20"
}

kotlin {
    js(IR) {
        nodejs()
    }
    sourceSets {
        val jsMain by getting { dependencies { implementation(project(":kairos-core")) } }
    }
}
