import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    kotlin("multiplatform") version "2.2.20"
}

val kairosPluginProject = project(":kairos-compiler-plugin")
val kairosPluginJarPath = kairosPluginProject.layout.buildDirectory.file("libs/kairos-compiler-plugin.jar")
val kairosOutDir = layout.buildDirectory.dir("kairos")

kotlin {
    jvm()
    js(IR) {
        nodejs()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_UMD)
            freeCompilerArgs.addAll(
                "-Xplugin=" + kairosPluginJarPath.get().asFile.absolutePath,
                "-P", "plugin:dev.kairos:outputDir=" + kairosOutDir.get().asFile.absolutePath,
                "-P", "plugin:dev.kairos:module=demo-web-feature",
            )
        }
    }
    sourceSets {
        val commonMain by getting { dependencies { implementation(project(":kairos-core")) } }
    }
}

val kairosCompileTasks = setOf("compileKotlinJs")
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(kairosPluginProject.tasks.named("jar"))
    inputs.file(kairosPluginJarPath)
    if (name in kairosCompileTasks) {
        outputs.dir(kairosOutDir)
        outputs.cacheIf { false }
    }
}
