package dev.isaacudy.udytils.architecture.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import java.util.Properties

/**
 * Wires the udytils architecture framework into a module:
 *
 *  - Creates an `architectureTest` source set with `architecture-core` and the JUnit 5 engine on
 *    its classpath. Because the architecture suite lives in its own source set, the module's plain
 *    `test` task does not run it.
 *  - When the `architecture { definition.set("…") }` extension names the module's
 *    `ArchitectureDefinition`, the test classes are **generated** into `build/generated/` from the
 *    compiled catalog (one `@TestFactory` per group) — nothing test-related is checked in.
 *    Hand-written additions can still live in `src/architectureTest/kotlin`.
 *  - Registers two standalone tasks, both of which always re-execute (never up-to-date):
 *      - `verifyArchitecture` — runs the architecture rules against the codebase.
 *      - `updateArchitectureDocumentation` — regenerates the generated docs (README + docs/)
 *        from the catalog, then verifies everything else as normal.
 *
 * Neither task is attached to `check` — wire `verifyArchitecture` into CI explicitly.
 */
class ArchitecturePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val version = loadVersion()
        val extension = project.extensions.create("architecture", ArchitectureExtension::class.java)

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val main = sourceSets.getByName("main")
        val architectureTest = sourceSets.create(SOURCE_SET_NAME) { sourceSet ->
            sourceSet.compileClasspath += main.output
            sourceSet.runtimeClasspath += main.output
        }

        val generate = project.tasks.register("generateArchitectureTests", GenerateArchitectureTestsTask::class.java) { task ->
            task.group = "verification"
            task.description = "Generates the architecture test classes from the module's ArchitectureDefinition."
            task.onlyIf { extension.definition.isPresent }
            task.definitionClass.set(extension.definition)
            task.definitionClasspath.from(main.runtimeClasspath)
            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/architecture/kotlin"))
        }
        // Register the generated sources with the source set's Kotlin directory set; wiring the
        // provider carries the task dependency, so compilation waits for generation.
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlinSources = (architectureTest as org.gradle.api.plugins.ExtensionAware)
                .extensions.getByName("kotlin") as org.gradle.api.file.SourceDirectorySet
            kotlinSources.srcDir(generate.flatMap { it.outputDirectory })
        }
        project.configurations.getByName("architectureTestImplementation").apply {
            extendsFrom(project.configurations.getByName("implementation"))
            project.configurations.findByName("api")?.let { extendsFrom(it) }
        }
        project.configurations.getByName("architectureTestRuntimeOnly")
            .extendsFrom(project.configurations.getByName("runtimeOnly"))

        project.dependencies.add(
            "architectureTestImplementation",
            "dev.isaacudy.udytils:architecture-core:$version",
        )
        project.dependencies.add(
            "architectureTestRuntimeOnly",
            "org.junit.jupiter:junit-jupiter:$JUNIT_VERSION",
        )

        val verify = project.tasks.register("verifyArchitecture", Test::class.java) { task ->
            task.group = "verification"
            task.description = "Runs the architecture rules against the codebase."
            task.testClassesDirs = architectureTest.output.classesDirs
            task.classpath = architectureTest.runtimeClasspath
            task.useJUnitPlatform()
            task.outputs.upToDateWhen { false }
        }
        project.tasks.register("updateArchitectureDocumentation", Test::class.java) { task ->
            task.group = "documentation"
            task.description = "Regenerates the architecture documentation (README + docs/) from the catalog."
            task.testClassesDirs = architectureTest.output.classesDirs
            task.classpath = architectureTest.runtimeClasspath
            task.useJUnitPlatform()
            task.outputs.upToDateWhen { false }
            task.environment("UPDATE_ARCHITECTURE_DOCS", "true")
            task.mustRunAfter(verify)
        }
    }

    private fun loadVersion(): String {
        val properties = Properties()
        javaClass.getResourceAsStream(VERSION_RESOURCE)?.use { properties.load(it) }
        return properties.getProperty("version")
            ?: error("Could not read the udytils architecture plugin version from $VERSION_RESOURCE")
    }

    private companion object {
        const val SOURCE_SET_NAME = "architectureTest"
        const val JUNIT_VERSION = "5.11.4"
        const val VERSION_RESOURCE = "/dev/isaacudy/udytils/architecture/gradle/version.properties"
    }
}
