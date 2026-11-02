package dev.kairos.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.io.File
import java.util.zip.ZipFile

/**
 * Configuration surface of the `dev.kairos` Gradle plugin.
 *
 * Everything has a default: applying the plugin is enough to fold `css { }` blocks in every
 * compilation of the project.
 */
open class KairosExtension {
    /** Module name written to the sidecar manifest; defaults to the Gradle project name. */
    var moduleName: String? = null

    /**
     * Explicit output directory (relative to the project directory) shared by every compilation.
     * By default each target/compilation gets `build/kairos/<target>/<compilation>`.
     */
    var outputDirectory: String? = null

    /** Targets that should not run the compiler plugin (for example `metadata`). */
    var disabledTargets: Set<String> = emptySet()

    /** Fail the build instead of silently falling back when a style cannot be folded. */
    var strict: Boolean = false

    /** Per-compilation stylesheet directories, filled while compilations are configured. */
    internal val styleOutputs: MutableList<Pair<String, Provider<Directory>>> = ArrayList()
}

/**
 * Wires the Kairos compiler plugin into every Kotlin compilation and makes the compiled
 * stylesheets travel with the project's jars.
 *
 * Library modules embed `META-INF/kairos/<module>/<target>-<compilation>` inside every jar. A
 * consumer's `kairosMergeStyles` task extracts those entries from the runtime classpath and merges
 * them with its own stylesheet, so a component library is consumed as a plain binary dependency
 * with no source access and no manual CSS plumbing.
 */
class KairosGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.extensions.create("kairos", KairosExtension::class.java)
        wireDependencyStyles(target)
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val extension = kotlinCompilation.target.project.extensions
            .findByType(KairosExtension::class.java)
        if (extension?.disabledTargets?.contains(kotlinCompilation.target.name) == true) return false
        // Common-metadata compilations never produce runtime artifacts; every real target
        // (jvm, js, native) folds the styles it will actually ship.
        return kotlinCompilation.platformType != KotlinPlatformType.common
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(KairosExtension::class.java) ?: KairosExtension()
        val moduleName = extension.moduleName ?: project.name
        // Plain `kotlin("jvm")` projects have no named target; "jvm" keeps every module's
        // layout predictable (build/kairos/<target>/<compilation>).
        val targetSegment = kotlinCompilation.target.name.ifBlank { "jvm" }
        val defaultOutput = project.layout.buildDirectory.dir(
            "kairos/" + targetSegment + "/" + kotlinCompilation.name,
        )
        val outputDir: Provider<Directory> = extension.outputDirectory
            ?.let { configured -> project.provider { project.layout.projectDirectory.dir(configured) } }
            ?: defaultOutput

        kotlinCompilation.compileTaskProvider.configure { task ->
            // The stylesheet is a side effect of compilation. Declaring it as a task output is
            // what lets Gradle restore it from the build cache and detect a missing artifact.
            task.outputs.dir(outputDir)
        }

        // Only production styles ship inside the jar: test compilations would create a cycle
        // (jar -> test compile -> test runtime depends on the main jar) and must not publish.
        if (kotlinCompilation.name == KotlinCompilation.MAIN_COMPILATION_NAME) {
            embedStyles(
                project,
                extension,
                moduleName,
                targetSegment,
                kotlinCompilation.name,
                outputDir,
                kotlinCompilation,
            )
        }

        return project.provider {
            listOf(
                SubpluginOption("outputDir", outputDir.get().asFile.absolutePath),
                SubpluginOption("module", moduleName),
                SubpluginOption("projectDir", project.projectDir.absolutePath),
                SubpluginOption("strict", extension.strict.toString()),
            )
        }
    }

    override fun getCompilerPluginId(): String = "dev.kairos"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact("dev.kairos", "kairos-compiler-plugin", KairosVersion.value)

    override fun getPluginArtifactForNative(): SubpluginArtifact = getPluginArtifact()

    /** Puts the compiled stylesheet of every compilation into every jar of the project. */
    private fun embedStyles(
        project: Project,
        extension: KairosExtension,
        moduleName: String,
        targetSegment: String,
        compilationName: String,
        outputDir: Provider<Directory>,
        kotlinCompilation: KotlinCompilation<*>,
    ) {
        val key = targetSegment + "-" + compilationName
        if (extension.styleOutputs.any { it.first == key }) return
        extension.styleOutputs += key to outputDir
        project.tasks.withType(Jar::class.java).configureEach { jar ->
            jar.from(outputDir) { spec -> spec.into("META-INF/kairos/" + moduleName + "/" + key) }
            // The stylesheet is produced by the compilation; the jar must wait for it.
            jar.dependsOn(kotlinCompilation.compileTaskProvider)
        }
    }

    /**
     * Consumer side: extract library stylesheets from runtime jars and merge them with the local
     * ones. Only wired for JVM projects (`java` plugin), where a runtime classpath exists.
     */
    private fun wireDependencyStyles(target: Project) {
        val cli = target.configurations.findByName("kairosCli")
            ?: target.configurations.create("kairosCli", org.gradle.api.Action { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                configuration.description = "Kairos CLI used to merge local and dependency stylesheets"
            })
        target.dependencies.add("kairosCli", "dev.kairos:kairos-cli:" + KairosVersion.value)

        target.plugins.withId("java") {
            val collected = target.layout.buildDirectory.dir("kairos/dependencies")
            if (target.tasks.findByName("kairosCollectStyles") == null) {
                target.tasks.register("kairosCollectStyles", org.gradle.api.Action { task ->
                    task.group = "kairos"
                    task.description = "Extracts compiled stylesheets from runtime dependency jars"
                    val runtime = target.configurations.named("runtimeClasspath")
                    task.inputs.files(runtime)
                    task.outputs.dir(collected)
                    task.doLast(org.gradle.api.Action {
                        val outputDir = collected.get().asFile
                        outputDir.deleteRecursively()
                        runtime.get().files
                            .filter { file -> file.isFile && file.name.endsWith(".jar") }
                            .sortedBy { file -> file.name }
                            .forEach { jar ->
                                ZipFile(jar).use { zip ->
                                    zip.entries().asSequence()
                                        .filter { entry -> !entry.isDirectory && entry.name.startsWith("META-INF/kairos/") }
                                        .forEach { entry ->
                                            val targetFile = File(
                                                outputDir,
                                                jar.nameWithoutExtension + "/" + entry.name.removePrefix("META-INF/kairos/"),
                                            )
                                            targetFile.parentFile.mkdirs()
                                            zip.getInputStream(entry).use { input ->
                                                targetFile.outputStream().use { output -> input.copyTo(output) }
                                            }
                                        }
                                }
                            }
                    })
                })
            }

            if (target.tasks.findByName("kairosMergeStyles") == null) {
                target.tasks.register("kairosMergeStyles", org.gradle.api.tasks.JavaExec::class.java, org.gradle.api.Action { task ->
                    task.group = "kairos"
                    task.description = "Merges this module's stylesheet with the stylesheets shipped by its dependencies"
                    task.dependsOn("kairosCollectStyles")
                    // Local styles are a compilation side effect; the merge must run after them.
                    if (target.tasks.findByName("classes") != null) task.dependsOn("classes")
                    task.classpath = cli
                    task.mainClass.set("dev.kairos.cli.MainKt")
                    task.doFirst(org.gradle.api.Action {
                        val extension = target.extensions.findByType(KairosExtension::class.java)
                        val output = target.layout.buildDirectory.dir("kairos/merged").get().asFile
                        output.mkdirs()
                        val localMaps = extension?.styleOutputs.orEmpty()
                            .map { entry -> entry.second.get().asFile.resolve("kairos.map.json") }
                            .filter { file -> file.exists() }
                        val dependencyMaps = collected.get().asFile
                            .walkTopDown()
                            .filter { file -> file.isFile && file.name == "kairos.map.json" }
                            .toList()
                        val maps = (localMaps + dependencyMaps).distinct()
                        task.args = listOf(
                            "merge",
                            "--out", output.resolve("kairos.css").absolutePath,
                            "--map-out", output.resolve("kairos.map.json").absolutePath,
                        ) + maps.flatMap { map -> listOf("--map", map.absolutePath) }
                    })
                })
            }
        }
    }
}

/** Version embedded at build time so the compiler plugin coordinate always matches. */
internal object KairosVersion {
    val value: String by lazy {
        val stream = KairosGradlePlugin::class.java
            .getResourceAsStream("/dev/kairos/gradle/version.properties")
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        text.lineSequence()
            .firstOrNull { it.startsWith("version=") }
            ?.removePrefix("version=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "0.0.0"
    }
}
