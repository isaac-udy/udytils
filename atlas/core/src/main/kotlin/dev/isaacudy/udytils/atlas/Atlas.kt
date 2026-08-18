package dev.isaacudy.udytils.atlas

import kotlinx.serialization.json.Json
import java.io.File

data class AtlasSummary(
    val nodes: Int,
    val edges: Int,
    val variants: Int,
    val unresolvedEdges: Int,
    val unmatchedGoldens: Int,
    val totalGoldens: Int,
    val outputPath: File,
)

fun generateAtlas(config: AtlasConfig): AtlasSummary {
    val resolved = config.resolved()

    val rawNodes = scanNodes(resolved)
    val knownDests = rawNodes.map { it.destinationName }.toSet()

    val rawEdges = scanEdges(resolved, knownDests)
    val goldens = scanGoldens(resolved)
    val manifest = assemble(resolved, rawNodes, rawEdges, goldens)

    resolved.outputDir.mkdirs()
    val json = Json { prettyPrint = true }
    File(resolved.outputDir, "manifest.json").writeText(
        json.encodeToString(AtlasManifest.serializer(), manifest)
    )

    copyGoldensToOutput(resolved, manifest)
    writeHtml(resolved, manifest)

    return AtlasSummary(
        nodes = manifest.nodes.size,
        edges = manifest.edges.size,
        variants = manifest.nodes.sumOf { it.variants.size },
        unresolvedEdges = manifest.unresolvedEdges.size,
        unmatchedGoldens = manifest.unmatchedGoldens,
        totalGoldens = manifest.totalGoldens,
        outputPath = resolved.outputDir,
    )
}
