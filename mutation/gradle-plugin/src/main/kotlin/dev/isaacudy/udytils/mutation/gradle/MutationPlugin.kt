package dev.isaacudy.udytils.mutation.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

/**
 * Mutation testing for the build: `./gradlew runMutants`.
 *
 * Apply at the root project and configure the `mutation { }` block. Mutation mode is on only when
 * the requested tasks include `runMutants`: the compiler plugin, the runtime switch and the test
 * specs exist in no other build.
 */
class MutationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project == project.rootProject) { "dev.isaacudy.udytils.mutation must be applied to the root project." }
        val extension = project.extensions.create("mutation", MutationExtension::class.java).apply {
            testTimeoutFactor.convention(2.0)
            testTimeoutConstantMillis.convention(1_000L)
        }
        val mutationMode = project.gradle.startParameter.taskNames.any { it.substringAfterLast(':') == RUN_MUTANTS }

        val driver = project.configurations.create("mutationDriver") {
            it.isCanBeConsumed = false
            it.isVisible = false
        }
        project.dependencies.add(driver.name, "$GROUP:mutation-junit-driver:$VERSION")

        if (mutationMode) {
            project.subprojects { sub ->
                listOf("org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.jvm").forEach { id ->
                    sub.pluginManager.withPlugin(id) { sub.pluginManager.apply(MutationModulePlugin::class.java) }
                }
            }
        }

        project.tasks.register(RUN_MUTANTS, RunMutantsTask::class.java) { task ->
            task.group = "verification"
            task.description = "Runs the catalog mutants against the JVM tests that execute them and writes build/reports/mutation."
            task.repositoryDirectory.set(project.layout.projectDirectory)
            task.driverClasspath.from(driver)
            task.classification.set(extension.classification)
            task.moduleExpectations.set(extension.moduleExpectations)
            task.rules.set(extension.encodedRules)
            task.ignored.set(extension.ignored)
            task.testTimeoutFactor.set(extension.testTimeoutFactor)
            task.testTimeoutConstantMillis.set(extension.testTimeoutConstantMillis)
            task.changedSince.set(project.providers.gradleProperty("mutation.changedSince"))
            task.onlyModules.set(project.providers.gradleProperty("mutation.modules").map { it.split(',').map(String::trim) })
            task.outputDirectory.set(project.layout.buildDirectory.dir("reports/mutation"))
            task.workDirectory.set(project.layout.buildDirectory.dir("mutation"))
            task.modules.set(project.provider {
                project.subprojects.associate { it.path to it.projectDir.relativeTo(project.rootDir).invariantSeparatorsPath }
            })
            if (mutationMode) {
                project.subprojects.forEach { sub ->
                    task.manifestDirectories.from(sub.layout.buildDirectory.dir("mutation/manifest"))
                    task.testSpecs.from(sub.layout.buildDirectory.dir("mutation/test-specs"))
                    task.dependsOn(sub.tasks.matching { it.name == WRITE_TEST_SPEC })
                }
            }
        }
    }

    internal companion object {
        const val RUN_MUTANTS = "runMutants"
        const val WRITE_TEST_SPEC = "writeMutationTestSpec"
        const val GROUP = "dev.isaacudy.udytils"
        const val PLUGIN_ID = "dev.isaacudy.udytils.mutation"

        val VERSION: String by lazy {
            Properties().apply {
                MutationPlugin::class.java.getResourceAsStream("version.properties")!!.use { load(it) }
            }.getProperty("version")
        }
    }
}
