package dev.isaacudy.udytils.atlas

import java.io.File

data class AtlasConfig(
    val repoRoot: File,
    val outputDir: File,
    val chromeEdges: List<ChromeEdge> = emptyList(),
    val projectName: String? = null,
    val sourceRoots: List<String>? = null,
    val goldenRoots: List<GoldenRoot>? = null,
    val extraExcludePaths: List<String> = emptyList(),
    val featureGroupFallbacks: List<Pair<String, String>> = emptyList(),
) {
    fun resolved(): ResolvedAtlasConfig {
        return ResolvedAtlasConfig(
            projectName = projectName ?: AtlasDiscovery.discoverProjectName(repoRoot),
            repoRoot = repoRoot,
            sourceRoots = sourceRoots ?: AtlasDiscovery.discoverSourceRoots(repoRoot),
            goldenRoots = goldenRoots ?: AtlasDiscovery.discoverGoldenRoots(repoRoot),
            outputDir = outputDir,
            chromeEdges = chromeEdges,
            featureGroupFallbacks = featureGroupFallbacks,
        )
    }
}

data class ResolvedAtlasConfig(
    val projectName: String,
    val repoRoot: File,
    val sourceRoots: List<String>,
    val goldenRoots: List<GoldenRoot>,
    val outputDir: File,
    val chromeEdges: List<ChromeEdge>,
    val featureGroupFallbacks: List<Pair<String, String>>,
)

data class GoldenRoot(
    val path: String,
    val moduleLabel: String,
)

data class ChromeEdge(
    val sourceDestination: String,
    val targetDestination: String,
)
