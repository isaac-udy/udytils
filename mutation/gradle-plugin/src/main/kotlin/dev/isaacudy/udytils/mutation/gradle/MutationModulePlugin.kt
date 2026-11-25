package dev.isaacudy.udytils.mutation.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Applied by [MutationPlugin] to each Kotlin module in mutation mode. Compiles the JVM main
 * compilation with the mutation compiler plugin and the runtime switch, declares the manifest
 * directory as an output of the compile task, and writes the JVM test task's launch settings
 * for the runner.
 */
class MutationModulePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        val state = target.extensions.create(STATE, MutationModuleState::class.java)
        // After every project is evaluated: the Android plugin registers testAndroidHostTest in its own afterEvaluate.
        target.gradle.projectsEvaluated {
            val project = target
            val host = project.mutationHost() ?: return@projectsEvaluated
            val candidates = if (host == MutationHosts.ANDROID) listOf("testAndroidHostTest") else listOf("jvmTest", "test")
            val testTaskName = candidates.firstOrNull { it in project.tasks.names } ?: return@projectsEvaluated
            // Eager in afterEvaluate: a provider mapped from the test task would make the spec depend on running the tests.
            val test = project.tasks.getByName(testTaskName) as Test
            val spec = project.tasks.register(MutationPlugin.WRITE_TEST_SPEC, WriteTestSpecTask::class.java) { task ->
                task.projectPath.set(project.path)
                task.testTask.set(testTaskName)
                task.classpath.from(test.classpath)
                task.testClassesDirs.from(test.testClassesDirs)
                task.systemProperties.set(project.provider { test.systemProperties.mapValues { (_, v) -> v.toString() } })
                task.jvmArgs.set(project.provider {
                    test.jvmArgs.orEmpty() + test.jvmArgumentProviders.flatMap { it.asArguments() } +
                        listOfNotNull(test.minHeapSize?.let { "-Xms$it" }, test.maxHeapSize?.let { "-Xmx$it" })
                })
                task.environment.set(project.provider {
                    test.environment.mapValues { (_, v) -> v.toString() }.filter { (k, v) -> System.getenv(k) != v }
                })
                task.workingDirectory.set(project.provider { test.workingDir.absolutePath })
                task.javaExecutable.set(test.javaLauncher.map { it.executablePath.asFile.absolutePath })
                task.specFile.set(project.layout.buildDirectory.file("mutation/test-specs/$testTaskName.json"))
            }
            state.specs.from(spec.flatMap { it.specFile })
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        if (kotlinCompilation.name != KotlinCompilation.MAIN_COMPILATION_NAME) return false
        return when (kotlinCompilation.target.project.mutationHost()) {
            MutationHosts.JVM -> kotlinCompilation.platformType == KotlinPlatformType.jvm
            MutationHosts.ANDROID -> kotlinCompilation.platformType == KotlinPlatformType.androidJvm
            else -> false
        }
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.rootProject.extensions.getByType(MutationExtension::class.java)
        kotlinCompilation.defaultSourceSet.dependencies {
            implementation("${MutationPlugin.GROUP}:mutation-runtime:${MutationPlugin.VERSION}")
        }
        val manifestDir = project.layout.buildDirectory.dir("mutation/manifest/${kotlinCompilation.target.name}${kotlinCompilation.name.replaceFirstChar(Char::uppercase)}")
        project.extensions.getByType(MutationModuleState::class.java).manifests
            .from(project.files(manifestDir).builtBy(kotlinCompilation.compileTaskProvider))
        kotlinCompilation.compileTaskProvider.configure { task ->
            task.outputs.dir(manifestDir).withPropertyName("mutationManifest")
            task.compilerOptions.freeCompilerArgs.add("-Xcompiler-plugin-order=${MutationPlugin.PLUGIN_ID}>$COMPOSE_PLUGIN_ID")
        }
        val buildDir = project.layout.buildDirectory.get().asFile.absolutePath
        return extension.packages.zip(manifestDir) { packages, dir ->
            listOf(SubpluginOption("manifestDir", dir.asFile.absolutePath), SubpluginOption("excludePath", buildDir)) +
                packages.map { SubpluginOption("include", it.removeSuffix(".*").removeSuffix("*")) }
        }
    }

    override fun getCompilerPluginId(): String = MutationPlugin.PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(MutationPlugin.GROUP, "mutation-compiler-plugin", MutationPlugin.VERSION)

    internal companion object {
        const val STATE = "mutationModule"
        const val COMPOSE_PLUGIN_ID = "androidx.compose.compiler.plugins.kotlin"
    }
}

/** The manifest directories and test spec that one module contributes to `runMutants` in this build. */
abstract class MutationModuleState {
    abstract val manifests: org.gradle.api.file.ConfigurableFileCollection
    abstract val specs: org.gradle.api.file.ConfigurableFileCollection
}
