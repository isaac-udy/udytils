package dev.isaacudy.udytils.atlas.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers a `generateUiAtlas` task that scans the applying project for Enro navigation
 * destinations and Paparazzi snapshot goldens, then generates an interactive HTML atlas
 * with a machine-readable `manifest.json`.
 *
 * Apply at the repo's root project and configure via the `atlas { }` extension.
 */
class AtlasPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("atlas", AtlasExtension::class.java)

        extension.chromeEdges.convention(emptyList())
        extension.outputDirectory.convention(project.layout.buildDirectory.dir("ui-atlas"))
        extension.extraExcludePaths.convention(emptyList())
        extension.featureGroupFallbacks.convention(emptyList())

        val projectDir = project.projectDir
        val gitmodulesFile = project.file(".gitmodules")

        val submodulePaths: List<String> = if (gitmodulesFile.exists()) {
            Regex("""path\s*=\s*(.+)""").findAll(gitmodulesFile.readText())
                .map { it.groupValues[1].trim() }
                .toList()
        } else {
            emptyList()
        }

        val inputFileTree = project.fileTree(projectDir) { tree ->
            tree.include("**/*.kt", "**/*.png")
            tree.exclude("**/build/**", "**/.*/**", "**/node_modules/**")
            for (subPath in submodulePaths) {
                tree.exclude("$subPath/**")
            }
        }

        project.tasks.register("generateUiAtlas", GenerateUiAtlasTask::class.java) { task ->
            task.group = "documentation"
            task.description = "Generates an interactive UI atlas from navigation destinations and snapshot goldens."
            task.repoRoot.set(projectDir.absolutePath)
            task.sourceFiles.set(inputFileTree)
            task.projectName.set(extension.projectName)
            task.chromeEdges.set(extension.chromeEdges)
            task.sourceRoots.set(extension.sourceRoots)
            task.goldenRoots.set(extension.goldenRoots)
            task.extraExcludePaths.set(extension.extraExcludePaths)
            task.featureGroupFallbacks.set(extension.featureGroupFallbacks)
            task.outputDirectory.set(extension.outputDirectory)
        }
    }
}
