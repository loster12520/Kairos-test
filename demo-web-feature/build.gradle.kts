import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kairos)
}

kotlin {
    jvm()
    js(IR) {
        nodejs()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_UMD)
        }
    }
    sourceSets {
        val commonMain by getting { dependencies { implementation(project(":kairos-core")) } }
    }
}

// The stylesheet of this module is written by the dev.kairos plugin next to the compilation
// output; see build/kairos/<target>/<compilation>/kairos.css.
