import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    kotlin("multiplatform") version "2.2.20"
}

val kairosPluginProject = project(":kairos-compiler-plugin")
val kairosPluginJarPath = kairosPluginProject.layout.buildDirectory.file("libs/kairos-compiler-plugin.jar")
val kairosOutDir = layout.buildDirectory.dir("kairos")
val jsOutputDir = layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin")
val siteDir = layout.buildDirectory.dir("site")
val referenceDir = layout.buildDirectory.dir("reference-site")

val featureProject = project(":demo-web-feature")
val featureOutDir = featureProject.layout.buildDirectory.dir("kairos")

kotlin {
    jvm()

    js(IR) {
        nodejs()
        binaries.executable()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_UMD)
            freeCompilerArgs.addAll(
                "-Xplugin=" + kairosPluginJarPath.get().asFile.absolutePath,
                "-P", "plugin:dev.kairos:outputDir=" + kairosOutDir.get().asFile.absolutePath,
                "-P", "plugin:dev.kairos:module=demo-web",
            )
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kairos-core"))
                implementation(project(":demo-web-feature"))
            }
        }
        val jvmMain by getting { dependencies { implementation(project(":kairos-core")) } }
        val jsMain by getting {
            dependencies {
                implementation(project(":kairos-core"))
                implementation(project(":kairos-browser"))
            }
        }
    }
}

// The plugin writes kairos.css / kairos.map.json as a *side effect* of compilation. Declaring
// that directory as a task output is what makes a clean build correct: without it Gradle can
// restore the JS compilation from the build cache without ever running the plugin.
val kairosCompileTasks = setOf("compileKotlinJs")
// The static HTML renderer must produce exactly the same class lists as the browser build, so
// the plugin runs on the JVM target too (extraction is a whole-module decision).
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>("compileKotlinJvm") {
    dependsOn(kairosPluginProject.tasks.named("jar"))
    inputs.file(kairosPluginJarPath)
    compilerOptions.freeCompilerArgs.addAll(
        "-Xplugin=" + kairosPluginJarPath.get().asFile.absolutePath,
        "-P", "plugin:dev.kairos:outputDir=" + layout.buildDirectory.dir("kairos-jvm").get().asFile.absolutePath,
        "-P", "plugin:dev.kairos:module=demo-web-jvm",
    )
    outputs.dir(layout.buildDirectory.dir("kairos-jvm"))
    outputs.cacheIf { false }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(kairosPluginProject.tasks.named("jar"))
    inputs.file(kairosPluginJarPath)
    if (name in kairosCompileTasks) {
        outputs.dir(kairosOutDir)
        outputs.cacheIf { false }
    }
}

// ---------------------------------------------------------------------------
// Verification entry points
// ---------------------------------------------------------------------------

val jvmCompilation = kotlin.jvm().compilations.getByName("main")
fun JavaExec.runtimeClasspath() {
    classpath = jvmCompilation.output.allOutputs + (jvmCompilation.runtimeDependencyFiles ?: files())
    mainClass.set("JvmMainKt")
}

val runJvmReference by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the demo on the JVM without the Kairos plugin (runtime fallback reference)."
    dependsOn("jvmMainClasses")
    runtimeClasspath()
    args(referenceDir.get().asFile.absolutePath)
}

val runJsTierOne by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the folded JavaScript bundle under Node."
    dependsOn("compileProductionExecutableKotlinJs")
    commandLine("node", jsOutputDir.get().asFile.resolve("kairos-demo.js").absolutePath)
}

// ---------------------------------------------------------------------------
// Static site assembly: render -> copy -> merge module maps -> prune
// ---------------------------------------------------------------------------

val renderSite by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Renders the static index.html with the JVM renderer."
    dependsOn("jvmMainClasses")
    runtimeClasspath()
    args(referenceDir.get().asFile.absolutePath)
}

val prepareSite by tasks.registering(Sync::class) {
    group = "build"
    dependsOn(renderSite, "compileProductionExecutableKotlinJs")
    into(siteDir)
    from(project.layout.projectDirectory.dir("web"))
    from(referenceDir) { include("index.html") }
    from(jsOutputDir) { include("*.js") }
}

// The tooling module is a plain JVM application; running it from the build keeps the merge and
// prune steps identical to what a user would run by hand.
val cliProject = project(":kairos-cli")
val cliJar = cliProject.layout.buildDirectory.file("libs/kairos-cli.jar")
// Cross project task lookup must stay lazy: ':kairos-cli' may not be evaluated yet while
// ':demo' is being configured.
val cliClasspath = files(cliJar)

val kairosMerge by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Merges every module's sidecar map into one layered stylesheet."
    dependsOn(prepareSite, ":demo-web-feature:compileKotlinJs", ":kairos-cli:jar")
    classpath = cliClasspath
    mainClass.set("dev.kairos.cli.MainKt")
    doFirst {
        classpath = classpath + cliProject.configurations.getByName("runtimeClasspath")
        val out = siteDir.get().asFile.resolve("kairos.css")
        args = listOf(
            "merge",
            "--out", out.absolutePath,
            "--map", kairosOutDir.get().asFile.resolve("kairos.map.json").absolutePath,
            "--map", featureOutDir.get().asFile.resolve("kairos.map.json").absolutePath,
        )
    }
}

val kairosPrune by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Drops rules whose class never appears in the shipped html / js."
    dependsOn(kairosMerge)
    classpath = cliClasspath
    mainClass.set("dev.kairos.cli.MainKt")
    doFirst {
        classpath = classpath + cliProject.configurations.getByName("runtimeClasspath")
        val site = siteDir.get().asFile
        val css = site.resolve("kairos.css")
        val units = site.listFiles { file -> file.isFile && (file.name.endsWith(".html") || file.name.endsWith(".js")) }
            ?.sortedBy { it.name } ?: emptyList()
        args = listOf("prune", "--css", css.absolutePath, "--out", css.absolutePath) +
            units.flatMap { listOf("--unit", it.absolutePath) }
    }
}

val assembleSite by tasks.registering {
    group = "build"
    description = "Assembles the merged, pruned static site into demo-web/build/site."
    dependsOn(kairosPrune)
    doLast {
        val site = siteDir.get().asFile
        logger.lifecycle("kairos site assembled: " + site.absolutePath)
        site.listFiles()?.sortedBy { it.name }?.forEach { logger.lifecycle("  " + it.name + " (" + it.length() + " bytes)") }
    }
}
