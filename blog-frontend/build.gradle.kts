import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kairos)
}

kotlin {
    js(IR) {
        nodejs()
        binaries.executable()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_UMD)
        }
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":kairos-core"))
                implementation(project(":kairos-browser"))
            }
        }
    }
}
