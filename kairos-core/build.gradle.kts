plugins {
    kotlin("multiplatform") version "2.2.20"
}

kotlin {
    jvm()
    js(IR) {
        nodejs()
    }
}
