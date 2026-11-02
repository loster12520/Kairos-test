plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

kotlin {
    jvm()
    js(IR) {
        nodejs()
    }
    sourceSets {
        val commonTest by getting {
            dependencies { implementation(libs.kotlin.test) }
        }
    }
}
