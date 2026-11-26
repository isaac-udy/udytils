package dev.isaacudy.udytils.trace.gradle

import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.util.Properties

abstract class TraceExtension {
    /** Package prefixes to instrument, such as `feature`; empty instruments every package outside `kotlin`. */
    abstract val packages: ListProperty<String>

    /**
     * False compiles the module without probes: the plugin still rewrites `onCalled`,
     * `onConstructed` and `declarationIdOf`, and publishes empty site manifests. Defaults to true.
     */
    abstract val instrument: Property<Boolean>
}

/**
 * Compiles every main compilation of the module with the trace compiler plugin and the runtime.
 * The JVM compilation's site manifests are published in [MANIFESTS_ELEMENTS] for
 * [TraceSiteTablePlugin].
 */
class TracePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create(EXTENSION, TraceExtension::class.java).instrument.convention(true)
        target.configurations.consumable(MANIFESTS_ELEMENTS) { configuration ->
            configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, target.objects.named(Usage::class.java, MANIFESTS_USAGE))
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.name == KotlinCompilation.MAIN_COMPILATION_NAME &&
            kotlinCompilation.platformType != KotlinPlatformType.common

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(TraceExtension::class.java)
        kotlinCompilation.defaultSourceSet.dependencies {
            implementation("$GROUP:trace-runtime:$VERSION")
        }
        val manifestDir = project.layout.buildDirectory.dir("trace/manifest/${kotlinCompilation.target.name}")
        kotlinCompilation.compileTaskProvider.configure { task ->
            task.outputs.dir(manifestDir).withPropertyName("traceManifest")
            task.compilerOptions.freeCompilerArgs.add("-Xcompiler-plugin-order=$PLUGIN_ID>$COMPOSE_PLUGIN_ID")
            // The mutation plugin mutates source code only; the probes then wrap the mutated bodies.
            task.compilerOptions.freeCompilerArgs.add("-Xcompiler-plugin-order=$MUTATION_PLUGIN_ID>$PLUGIN_ID")
        }
        if (kotlinCompilation.platformType == KotlinPlatformType.jvm) {
            project.artifacts.add(MANIFESTS_ELEMENTS, manifestDir) { it.builtBy(kotlinCompilation.compileTaskProvider) }
        }
        val buildDir = project.layout.buildDirectory.get().asFile.absolutePath
        return extension.packages.zip(manifestDir) { packages, dir -> packages to dir }.zip(extension.instrument) { (packages, dir), instrument ->
            listOf(
                SubpluginOption("manifestDir", dir.asFile.absolutePath),
                SubpluginOption("excludePath", buildDir),
                SubpluginOption("instrument", instrument.toString()),
            ) + packages.map { SubpluginOption("include", it.removeSuffix(".*").removeSuffix("*")) }
        }
    }

    override fun getCompilerPluginId(): String = PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(GROUP, "trace-compiler-plugin", VERSION)

    companion object {
        const val GROUP = "dev.isaacudy.udytils"
        const val PLUGIN_ID = "dev.isaacudy.udytils.trace"
        const val EXTENSION = "trace"
        const val MANIFESTS_ELEMENTS = "traceSiteManifestsElements"
        const val MANIFESTS_USAGE = "udytils-trace-site-manifests"
        private const val COMPOSE_PLUGIN_ID = "androidx.compose.compiler.plugins.kotlin"
        private const val MUTATION_PLUGIN_ID = "dev.isaacudy.udytils.mutation"

        val VERSION: String by lazy {
            Properties().apply {
                TracePlugin::class.java.getResourceAsStream("version.properties")!!.use { load(it) }
            }.getProperty("version")
        }
    }
}
