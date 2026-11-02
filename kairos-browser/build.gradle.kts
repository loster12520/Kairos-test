plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

kotlin {
    js(IR) {
        nodejs()
    }
    sourceSets {
        val jsMain by getting { dependencies { implementation(project(":kairos-core")) } }
    }
}
