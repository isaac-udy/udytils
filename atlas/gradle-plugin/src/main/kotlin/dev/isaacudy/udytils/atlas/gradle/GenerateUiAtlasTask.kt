package dev.isaacudy.udytils.atlas.gradle

import dev.isaacudy.udytils.atlas.AtlasConfig
import dev.isaacudy.udytils.atlas.AtlasDiscovery
import dev.isaacudy.udytils.atlas.ChromeEdge
import dev.isaacudy.udytils.atlas.generateAtlas
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateUiAtlasTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: Property<FileTree>

    @get:Input
    abstract val repoRoot: Property<String>

    @get:Input
    @get:Optional
    abstract val projectName: Property<String>

    @get:Input
    abstract val chromeEdges: ListProperty<Pair<String, String>>

    @get:Input
    @get:Optional
    abstract val sourceRoots: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val goldenRoots: ListProperty<String>

    @get:Input
    abstract val extraExcludePaths: ListProperty<String>

    @get:Input
    abstract val featureGroupFallbacks: ListProperty<Pair<String, String>>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = java.io.File(repoRoot.get())
        val config = AtlasConfig(
            repoRoot = root,
            outputDir = outputDirectory.get().asFile,
            chromeEdges = chromeEdges.get().map { ChromeEdge(it.first, it.second) },
            projectName = projectName.orNull,
            sourceRoots = sourceRoots.orNull?.takeIf { it.isNotEmpty() },
            goldenRoots = goldenRoots.orNull?.takeIf { it.isNotEmpty() }?.map { AtlasDiscovery.goldenRootFor(it) },
            extraExcludePaths = extraExcludePaths.get(),
            featureGroupFallbacks = featureGroupFallbacks.get(),
        )

        val summary = generateAtlas(config)

        logger.lifecycle("UI Atlas generated:")
        logger.lifecycle("  Nodes:             ${summary.nodes}")
        logger.lifecycle("  Edges:             ${summary.edges}")
        logger.lifecycle("  Variants:          ${summary.variants}")
        logger.lifecycle("  Unresolved edges:  ${summary.unresolvedEdges}")
        logger.lifecycle("  Unmatched goldens: ${summary.unmatchedGoldens}")
        logger.lifecycle("  Total goldens:     ${summary.totalGoldens}")
        logger.lifecycle("  Output:            ${summary.outputPath.absolutePath}")
    }
}
