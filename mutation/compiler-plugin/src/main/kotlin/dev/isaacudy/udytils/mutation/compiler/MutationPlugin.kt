package dev.isaacudy.udytils.mutation.compiler

import dev.isaacudy.udytils.mutation.compiler.operators.MutationOperators
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

const val PLUGIN_ID = "dev.isaacudy.udytils.mutation"

val MANIFEST_DIR = CompilerConfigurationKey<String>("mutation manifest directory")
val INCLUDED_PACKAGES = CompilerConfigurationKey<List<String>>("mutation package prefixes")
val EXCLUDED_PATHS = CompilerConfigurationKey<List<String>>("mutation excluded directories")
val OPERATORS = CompilerConfigurationKey<List<String>>("mutation operator IDs")

class MutationCommandLineProcessor : CommandLineProcessor {
    override val pluginId = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption("manifestDir", "<path>", "Directory that receives one mutant manifest per source file", required = false),
        CliOption("include", "<package prefix>", "Package prefix to mutate", required = false, allowMultipleOccurrences = true),
        CliOption("excludePath", "<directory>", "Directory whose source files are not mutated, such as generated sources", required = false, allowMultipleOccurrences = true),
        CliOption("operator", "<id>", "Mutation operator to compile in; without the option, every default operator", required = false, allowMultipleOccurrences = true),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "manifestDir" -> configuration.put(MANIFEST_DIR, value)
            "include" -> configuration.appendList(INCLUDED_PACKAGES, value)
            "excludePath" -> configuration.appendList(EXCLUDED_PATHS, value)
            "operator" -> configuration.appendList(OPERATORS, value)
        }
    }
}

class MutationPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId = PLUGIN_ID
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val manifestDir = configuration.get(MANIFEST_DIR) ?: return
        IrGenerationExtension.registerExtension(
            MutationIrExtension(
                manifestDir = manifestDir,
                includedPackages = configuration.getList(INCLUDED_PACKAGES),
                excludedPaths = configuration.getList(EXCLUDED_PATHS),
                operators = MutationOperators.select(configuration.getList(OPERATORS)),
            ),
        )
    }
}
