package dev.isaacudy.udytils.atlas

import kotlinx.serialization.Serializable

@Serializable
data class AtlasManifest(
    val projectName: String,
    val generatedAt: String,
    val nodes: List<AtlasNode>,
    val edges: List<AtlasEdge>,
    val unresolvedEdges: List<UnresolvedEdge>,
    val unmatchedGoldens: Int,
    val totalGoldens: Int,
)

@Serializable
data class AtlasNode(
    val qualifiedName: String,
    val destinationName: String,
    val screenName: String,
    val displayName: String,
    val featureGroup: String,
    val moduleLabel: String,
    val sourceFile: String,
    val packageName: String,
    val isWithResult: Boolean,
    val navigationPath: String?,
    val isShellActive: Boolean,
    val variants: List<AtlasVariant>,
    val defaultVariantIndex: Int,
    val synthetic: Boolean = false,
)

@Serializable
data class AtlasVariant(
    val label: String,
    val imagePath: String,
    val width: Int,
    val height: Int,
)

@Serializable
data class AtlasEdge(
    val source: String,
    val target: String,
    val kind: EdgeKind,
)

@Serializable
enum class EdgeKind {
    OPEN, RESULT, CHROME
}

@Serializable
data class UnresolvedEdge(
    val file: String,
    val line: Int,
    val text: String,
)
