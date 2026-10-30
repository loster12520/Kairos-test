import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    kotlin("multiplatform") version "2.2.20"
}

val kairosPluginJar = project(":kairos-compiler-plugin").layout.buildDirectory.file("libs/kairos-compiler-plugin.jar")
val kairosOut = layout.buildDirectory.dir("kairos")

kotlin {
    js(IR) {
        nodejs()
        binaries.executable()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_UMD)
            freeCompilerArgs.addAll(
                "-Xplugin=" + kairosPluginJar.get().asFile.absolutePath,
                "-P", "plugin:dev.kairos:outputDir=" + kairosOut.get().asFile.absolutePath,
                "-P", "plugin:dev.kairos:module=blog-frontend",
            )
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(":kairos-compiler-plugin:jar")
    inputs.file(kairosPluginJar)
    if (name == "compileKotlinJs") {
        outputs.dir(kairosOut)
        outputs.cacheIf { false }
    }
}
