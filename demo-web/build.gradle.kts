import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kairos)
}

val jsOutputDir = layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin")
val siteDir = layout.buildDirectory.dir("site")
val referenceDir = layout.buildDirectory.dir("reference-site")
// The dev.kairos plugin writes one stylesheet per target/compilation. The static renderer and
// the browser bundle must agree, so both sides are folded by the same plugin.
val jsStylesDir = layout.buildDirectory.dir("kairos/js/main")
val jvmStylesDir = layout.buildDirectory.dir("kairos/jvm/main")
val featureStylesDir = project(":demo-web-feature").layout.buildDirectory.dir("kairos/js/main")

kotlin {
    jvm()

    js(IR) {
        nodejs()
        binaries.executable()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_UMD)
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

// ---------------------------------------------------------------------------
// Verification entry points
// ---------------------------------------------------------------------------

val jvmCompilation = kotlin.jvm().compilations.getByName("main")
fun JavaExec.jvmRuntimeClasspath() {
    classpath = jvmCompilation.output.allOutputs + (jvmCompilation.runtimeDependencyFiles ?: files())
    mainClass.set("JvmMainKt")
}

val runJvmReference = tasks.register<JavaExec>("runJvmReference") {
    group = "verification"
    description = "Renders the demo through the JVM path (static HTML class lists)."
    dependsOn("jvmMainClasses")
    jvmRuntimeClasspath()
    args(referenceDir.get().asFile.absolutePath)
}

val runJsTierOne = tasks.register<Exec>("runJsTierOne") {
    group = "verification"
    description = "Runs the folded JavaScript bundle under Node."
    dependsOn("compileProductionExecutableKotlinJs")
    commandLine("node", jsOutputDir.get().asFile.resolve("kairos-demo-web.js").absolutePath)
}

// ---------------------------------------------------------------------------
// Static site assembly: render -> copy -> merge module maps -> prune
// ---------------------------------------------------------------------------

val renderSite = tasks.register<JavaExec>("renderSite") {
    group = "build"
    description = "Renders the static index.html with the JVM renderer."
    dependsOn("jvmMainClasses")
    jvmRuntimeClasspath()
    args(referenceDir.get().asFile.absolutePath)
}

val prepareSite = tasks.register<Sync>("prepareSite") {
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
val cliJar = cliProject.layout.buildDirectory.file("libs/" + cliProject.name + "-" + cliProject.version + ".jar")
// Cross project task lookup must stay lazy: ':kairos-cli' may not be evaluated yet while
// ':demo' is being configured. The configuration is resolved in the configuration phase
// because Gradle 9 forbids resolving another project's configuration from a task action.
val cliClasspath = files(cliJar)

val kairosMerge = tasks.register<JavaExec>("kairosMerge") {
    group = "build"
    description = "Merges every module's sidecar map into one layered stylesheet."
    dependsOn(prepareSite, ":demo-web-feature:compileKotlinJs", ":kairos-cli:jar")
    classpath = cliClasspath
    mainClass.set("dev.kairos.cli.MainKt")
    doFirst {
        val out = siteDir.get().asFile.resolve("kairos.css")
        args = listOf(
            "merge",
            "--out", out.absolutePath,
            "--map", jsStylesDir.get().asFile.resolve("kairos.map.json").absolutePath,
            "--map", featureStylesDir.get().asFile.resolve("kairos.map.json").absolutePath,
        )
    }
}

val kairosPrune = tasks.register<JavaExec>("kairosPrune") {
    group = "build"
    description = "Drops rules whose class never appears in the shipped html / js."
    dependsOn(kairosMerge)
    classpath = cliClasspath
    mainClass.set("dev.kairos.cli.MainKt")
    doFirst {
        val site = siteDir.get().asFile
        val css = site.resolve("kairos.css")
        val units = site.listFiles { file -> file.isFile && (file.name.endsWith(".html") || file.name.endsWith(".js")) }
            ?.sortedBy { it.name } ?: emptyList()
        args = listOf(
            "prune",
            "--css", css.absolutePath,
            "--out", css.absolutePath,
            "--map", site.resolve("kairos.map.json").absolutePath,
        ) + units.flatMap { listOf("--unit", it.absolutePath) }
    }
}

cliProject.afterEvaluate {
    // Force resolution while still in the configuration phase: Gradle 9 rejects resolving
    // another project's configuration from inside a task action.
    val runtimeClasspath = configurations.getByName("runtimeClasspath").files
    kairosMerge.configure { classpath = files(cliJar, runtimeClasspath) }
    kairosPrune.configure { classpath = files(cliJar, runtimeClasspath) }
}

val assembleSite = tasks.register("assembleSite") {
    group = "build"
    description = "Assembles the merged, pruned static site into demo-web/build/site."
    dependsOn(kairosPrune)
    doLast {
        val site = siteDir.get().asFile
        logger.lifecycle("kairos site assembled: " + site.absolutePath)
        site.listFiles()?.sortedBy { it.name }?.forEach { logger.lifecycle("  " + it.name + " (" + it.length() + " bytes)") }
    }
}
