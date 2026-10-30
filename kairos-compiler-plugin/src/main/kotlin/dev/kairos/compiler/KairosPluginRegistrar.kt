package dev.kairos.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File

val KEY_OUTPUT_DIR: CompilerConfigurationKey<String> =
    CompilerConfigurationKey.create("kairos output directory")
val KEY_MODULE: CompilerConfigurationKey<String> =
    CompilerConfigurationKey.create("kairos module name")

@OptIn(ExperimentalCompilerApi::class)
class KairosCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dev.kairos"

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "outputDir",
            valueDescription = "<path>",
            description = "directory that receives kairos.css / kairos.map.json / kairos-report.txt",
            required = false,
            allowMultipleOccurrences = false,
        ),
        CliOption(
            optionName = "module",
            valueDescription = "<name>",
            description = "logical module name recorded in the sidecar map",
            required = false,
            allowMultipleOccurrences = false,
        ),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "outputDir" -> configuration.put(KEY_OUTPUT_DIR, value)
            "module" -> configuration.put(KEY_MODULE, value)
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
class KairosCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val outputDir = configuration.get(KEY_OUTPUT_DIR) ?: return
        val module = configuration.get(KEY_MODULE) ?: "module"
        val collector = KairosCollector(File(outputDir), moduleName = module)
        IrGenerationExtension.registerExtension(KairosIrGenerationExtension(collector))
        // The IR extension can run more than once per module (klib pass + link pass), so the
        // artifacts are written once, at the very end of the compilation.
        registerDisposable(object : CompilerPluginRegistrar.PluginDisposable {
            override fun dispose() {
                // Artifacts first, then hard failures: the report file is what explains the
                // failure, so it has to be on disk even when the build is about to stop.
                // Artifacts first, then hard failures: the report file is what explains the
                // failure, so it has to be on disk even when the build is about to stop.
                try {
                    collector.flush()
                } catch (failure: Throwable) {
                    // A missing stylesheet would silently change the page, so this must stop the build.
                    throw IllegalStateException("[kairos] failed to write artifacts: " + failure.message, failure)
                }
                collector.throwIfFailed()
            }
        })
    }
}
