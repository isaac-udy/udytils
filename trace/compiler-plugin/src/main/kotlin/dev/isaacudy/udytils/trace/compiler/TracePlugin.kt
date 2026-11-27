package dev.isaacudy.udytils.trace.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

const val PLUGIN_ID = "dev.isaacudy.udytils.trace"

val MANIFEST_DIR = CompilerConfigurationKey<String>("trace manifest directory")
val INCLUDED_PACKAGES = CompilerConfigurationKey<List<String>>("trace package prefixes")
val EXCLUDED_PATHS = CompilerConfigurationKey<List<String>>("trace excluded directories")
val INSTRUMENT = CompilerConfigurationKey<Boolean>("trace instruments function bodies")

class TraceCommandLineProcessor : CommandLineProcessor {
    override val pluginId = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption("manifestDir", "<path>", "Directory that receives one site manifest per source file", required = true),
        CliOption("include", "<package prefix>", "Package prefix to instrument", required = false, allowMultipleOccurrences = true),
        CliOption("excludePath", "<directory>", "Directory whose source files are not instrumented, such as generated sources", required = false, allowMultipleOccurrences = true),
        CliOption("instrument", "true|false", "False rewrites rule references only and adds no probes", required = false),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "manifestDir" -> configuration.put(MANIFEST_DIR, value)
            "include" -> configuration.appendList(INCLUDED_PACKAGES, value)
            "excludePath" -> configuration.appendList(EXCLUDED_PATHS, value)
            "instrument" -> configuration.put(INSTRUMENT, value.toBooleanStrict())
        }
    }
}

class TracePluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId = PLUGIN_ID
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val manifestDir = configuration.get(MANIFEST_DIR) ?: return
        IrGenerationExtension.registerExtension(
            TraceIrExtension(
                manifestDir = manifestDir,
                includedPackages = configuration.getList(INCLUDED_PACKAGES),
                excludedPaths = configuration.getList(EXCLUDED_PATHS),
                instrument = configuration.get(INSTRUMENT) ?: true,
            ),
        )
    }
}
