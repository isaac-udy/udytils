package dev.isaacudy.udytils.trace.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer

abstract class TraceSiteTableExtension {
    /** The architecture `classification.json` that `classifyArchitecture` writes. */
    abstract val classification: RegularFileProperty

    /** The directory that the classification's declaration paths are relative to; the root project by default. */
    abstract val rootDirectory: DirectoryProperty

    /** The package of the generated `TraceSiteTable` and `Constructs` objects. */
    abstract val packageName: Property<String>
}

/**
 * Generates `TraceSiteTable`, which maps each site of the modules in the `traceSites`
 * configuration to its Constructs, and `Constructs`, one constant per Construct ID.
 */
class TraceSiteTablePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("traceSiteTable", TraceSiteTableExtension::class.java)
        extension.rootDirectory.convention(project.rootProject.layout.projectDirectory)
        val sites = project.configurations.dependencyScope(SITES)
        val manifests = project.configurations.resolvable("traceSiteManifests") { configuration ->
            configuration.extendsFrom(sites.get())
            configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, TracePlugin.MANIFESTS_USAGE))
        }
        val generate = project.tasks.register("generateTraceSiteTable", GenerateTraceSiteTableTask::class.java) { task ->
            task.manifests.from(manifests)
            task.classification.set(extension.classification)
            task.rootDirectory.set(extension.rootDirectory)
            task.packageName.set(extension.packageName)
            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/trace/kotlin"))
        }
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") { addSources(project, "commonMain", generate) }
        project.plugins.withId("org.jetbrains.kotlin.jvm") { addSources(project, "main", generate) }
    }

    private fun addSources(project: Project, sourceSet: String, generate: org.gradle.api.tasks.TaskProvider<GenerateTraceSiteTableTask>) {
        val kotlin = project.extensions.getByName("kotlin") as KotlinSourceSetContainer
        kotlin.sourceSets.named(sourceSet) { it.kotlin.srcDir(generate.flatMap { task -> task.outputDirectory }) }
    }

    companion object {
        const val SITES = "traceSites"
    }
}
