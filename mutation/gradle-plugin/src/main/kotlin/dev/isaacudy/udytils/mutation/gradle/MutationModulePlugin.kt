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
        target.afterEvaluate { project ->
            val testTaskName = listOf("jvmTest", "test").firstOrNull { it in project.tasks.names } ?: return@afterEvaluate
            // Eager in afterEvaluate: a provider mapped from the test task would make the spec depend on running the tests.
            val test = project.tasks.getByName(testTaskName) as Test
            project.tasks.register(MutationPlugin.WRITE_TEST_SPEC, WriteTestSpecTask::class.java) { task ->
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
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.name == KotlinCompilation.MAIN_COMPILATION_NAME &&
            kotlinCompilation.platformType == KotlinPlatformType.jvm

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.rootProject.extensions.getByType(MutationExtension::class.java)
        kotlinCompilation.defaultSourceSet.dependencies {
            implementation("${MutationPlugin.GROUP}:mutation-runtime:${MutationPlugin.VERSION}")
        }
        val manifestDir = project.layout.buildDirectory.dir("mutation/manifest/${kotlinCompilation.target.name}${kotlinCompilation.name.replaceFirstChar(Char::uppercase)}")
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

    private companion object {
        const val COMPOSE_PLUGIN_ID = "androidx.compose.compiler.plugins.kotlin"
    }
}
