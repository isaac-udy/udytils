package dev.isaacudy.udytils.atlas

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssemblerTest {

    private fun resolvedConfig(
        chromeEdges: List<ChromeEdge> = emptyList(),
        featureGroupFallbacks: List<Pair<String, String>> = emptyList(),
    ) = ResolvedAtlasConfig(
        projectName = "test",
        repoRoot = File("."),
        sourceRoots = emptyList(),
        goldenRoots = emptyList(),
        outputDir = File("out"),
        chromeEdges = chromeEdges,
        featureGroupFallbacks = featureGroupFallbacks,
    )

    private fun rawNode(
        name: String,
        pkg: String = "com.test",
        module: String = "mod",
        isWithResult: Boolean = false,
    ) = RawNode(
        qualifiedName = "$pkg.$name",
        destinationName = name,
        screenName = name.removeSuffix("Destination"),
        featureGroup = "test",
        moduleLabel = module,
        sourceFile = "src/$name.kt",
        packageName = pkg,
        isWithResult = isWithResult,
        navigationPath = null,
        isShellActive = false,
    )

    // --- golden to node matching ---

    @Test
    fun `matches golden to node via package path`() {
        val node = rawNode("HomeDestination", pkg = "com.test.feature.core.client.ui")
        val golden = GoldenFile(
            relativePath = "feature/core/client/src/snapshots/images/com/test/feature/core/client/ui/HomeScreenPreview.png",
            fileName = "HomeScreenPreview",
            packagePath = "com.test.feature.core.client.ui",
            width = 320, height = 480,
            moduleLabel = "feature:core:client",
        )
        val result = assignGoldensToNodes(listOf(node), listOf(golden))
        assertEquals(1, result.size)
        assertTrue(result.containsKey(node.qualifiedName))
        assertEquals("Default", result[node.qualifiedName]!![0].label)
    }

    @Test
    fun `derives variant label from golden name`() {
        val node = rawNode("HomeDestination", pkg = "com.test.ui")
        val goldens = listOf(
            GoldenFile("path/HomeScreenPreview.png", "HomeScreenPreview", "com.test.ui", 320, 480, "mod"),
            GoldenFile("path/HomeScreenLoadedPreview.png", "HomeScreenLoadedPreview", "com.test.ui", 320, 480, "mod"),
            GoldenFile("path/HomeScreenErrorPreview.png", "HomeScreenErrorPreview", "com.test.ui", 320, 480, "mod"),
        )
        val result = assignGoldensToNodes(listOf(node), goldens)
        val labels = result[node.qualifiedName]!!.map { it.label }.toSet()
        assertEquals(setOf("Default", "Loaded", "Error"), labels)
    }

    @Test
    fun `picks Default variant as default index`() {
        val variants = listOf(
            AtlasVariant("Alpha", "a.png", 100, 100),
            AtlasVariant("Default", "b.png", 100, 100),
            AtlasVariant("Zeta", "c.png", 100, 100),
        )
        assertEquals(1, pickDefaultVariant(variants))
    }

    @Test
    fun `picks Loaded variant when no Default`() {
        val variants = listOf(
            AtlasVariant("Error", "a.png", 100, 100),
            AtlasVariant("Loaded", "b.png", 100, 100),
        )
        assertEquals(1, pickDefaultVariant(variants))
    }

    @Test
    fun `picks shortest label as last resort`() {
        val variants = listOf(
            AtlasVariant("LongLabel", "a.png", 100, 100),
            AtlasVariant("Ab", "b.png", 100, 100),
        )
        assertEquals(1, pickDefaultVariant(variants))
    }

    // --- edge resolution ---

    @Test
    fun `resolves edge between source screen and unique target`() {
        val nodes = listOf(
            rawNode("HomeDestination"),
            rawNode("SettingsDestination"),
        )
        val edges = listOf(
            RawEdge("src/HomeViewModel.kt", 10, "Home", "SettingsDestination", "handle.open(SettingsDestination)", true),
        )
        val manifest = assemble(resolvedConfig(), nodes, edges, emptyList())
        assertEquals(1, manifest.edges.size)
        assertEquals("com.test.HomeDestination", manifest.edges[0].source)
        assertEquals("com.test.SettingsDestination", manifest.edges[0].target)
        assertEquals(EdgeKind.OPEN, manifest.edges[0].kind)
    }

    @Test
    fun `resolves edge as RESULT when target has WithResult`() {
        val nodes = listOf(
            rawNode("HomeDestination"),
            rawNode("EditDestination", isWithResult = true),
        )
        val edges = listOf(
            RawEdge("src/HomeViewModel.kt", 10, "Home", "EditDestination", "handle.open(EditDestination)", true),
        )
        val manifest = assemble(resolvedConfig(), nodes, edges, emptyList())
        assertEquals(EdgeKind.RESULT, manifest.edges[0].kind)
    }

    // --- disambiguation ---

    @Test
    fun `disambiguates by same package first, then same module`() {
        val nodes = listOf(
            rawNode("SourceDestination", pkg = "com.test.a", module = "mod-a"),
            rawNode("TargetDestination", pkg = "com.test.a", module = "mod-a"),
            rawNode("TargetDestination", pkg = "com.test.b", module = "mod-b"),
        )
        val edges = listOf(
            RawEdge("src/SourceViewModel.kt", 10, "Source", "TargetDestination", ".open(TargetDestination)", true),
        )
        val manifest = assemble(resolvedConfig(), nodes, edges, emptyList())
        assertEquals(1, manifest.edges.size)
        assertEquals("com.test.a.TargetDestination", manifest.edges[0].target)
    }

    // --- unresolved edges ---

    @Test
    fun `reports unresolved edges`() {
        val nodes = listOf(rawNode("HomeDestination"))
        val edges = listOf(
            RawEdge("src/HomeViewModel.kt", 5, "Home", "UnknownDestination", ".open(UnknownDestination)", false),
        )
        val manifest = assemble(resolvedConfig(), nodes, edges, emptyList())
        assertEquals(0, manifest.edges.size)
        assertEquals(1, manifest.unresolvedEdges.size)
    }

    // --- chrome edges ---

    @Test
    fun `chrome edges are added when both source and target resolve uniquely`() {
        val nodes = listOf(
            rawNode("HomeDestination"),
            rawNode("SettingsDestination"),
        )
        val config = resolvedConfig(chromeEdges = listOf(ChromeEdge("HomeDestination", "SettingsDestination")))
        val manifest = assemble(config, nodes, emptyList(), emptyList())
        assertEquals(1, manifest.edges.size)
        assertEquals(EdgeKind.CHROME, manifest.edges[0].kind)
    }

    // --- synthetic nodes ---

    @Test
    fun `creates synthetic nodes for unmatched goldens in packages without destinations`() {
        val nodes = listOf(rawNode("HomeDestination", pkg = "com.test.ui"))
        val goldens = listOf(
            GoldenFile("path/orphan/SomePreview.png", "SomePreview", "com.standalone.widget", 320, 480, "mod"),
        )
        val manifest = assemble(resolvedConfig(), nodes, emptyList(), goldens)
        val synthetic = manifest.nodes.filter { it.synthetic }
        assertEquals(1, synthetic.size)
        assertEquals("synthetic.com.standalone.widget", synthetic[0].qualifiedName)
    }
}
