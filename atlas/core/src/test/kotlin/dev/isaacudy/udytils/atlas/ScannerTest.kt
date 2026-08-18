package dev.isaacudy.udytils.atlas

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ScannerTest {

    private fun fixture(): File {
        val root = kotlin.io.path.createTempDirectory("atlas-scanner").toFile()
        root.deleteOnExit()
        return root
    }

    private fun write(root: File, path: String, vararg lines: String) {
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString("\n"))
    }

    // --- node detection ---

    @Test
    fun `detects a NavigationDestination node`() {
        val root = fixture()
        write(root, "feature/core/client/src/commonMain/kotlin/feature/core/client/ui/CoreScreen.kt",
            "package feature.core.client.ui",
            "",
            "@NavigationDestination(CoreDestination::class)",
            "fun CoreScreen() { }",
        )
        write(root, "feature/core/client/src/commonMain/kotlin/feature/core/client/ui/CoreDestination.kt",
            "package feature.core.client.ui",
            "",
            "data object CoreDestination : NavigationKey",
        )
        val config = AtlasConfig(repoRoot = root, outputDir = File(root, "out"),
            sourceRoots = listOf("feature/core/client/src/commonMain/kotlin")).resolved()
        val nodes = scanNodes(config)
        assertEquals(1, nodes.size)
        assertEquals("CoreDestination", nodes[0].destinationName)
        assertEquals("Core", nodes[0].screenName)
        assertEquals("feature.core.client.ui.CoreDestination", nodes[0].qualifiedName)
    }

    @Test
    fun `detects WithResult from destination file`() {
        val root = fixture()
        write(root, "src/commonMain/kotlin/ui/EditDestination.kt",
            "package ui",
            "",
            "data class EditDestination(val id: String) : NavigationKey.WithResult<String>",
        )
        write(root, "src/commonMain/kotlin/ui/EditScreen.kt",
            "package ui",
            "",
            "@NavigationDestination(EditDestination::class)",
            "fun EditScreen() { }",
        )
        val config = AtlasConfig(repoRoot = root, outputDir = File(root, "out"),
            sourceRoots = listOf("src/commonMain/kotlin")).resolved()
        val nodes = scanNodes(config)
        assertEquals(1, nodes.size)
        assertTrue(nodes[0].isWithResult)
    }

    @Test
    fun `extracts NavigationPath from destination file`() {
        val root = fixture()
        write(root, "src/commonMain/kotlin/ui/HomeDestination.kt",
            "package ui",
            "",
            """@NavigationPath("/home")""",
            "data object HomeDestination : NavigationKey",
        )
        write(root, "src/commonMain/kotlin/ui/HomeScreen.kt",
            "package ui",
            "",
            "@NavigationDestination(HomeDestination::class)",
            "fun HomeScreen() { }",
        )
        val config = AtlasConfig(repoRoot = root, outputDir = File(root, "out"),
            sourceRoots = listOf("src/commonMain/kotlin")).resolved()
        val nodes = scanNodes(config)
        assertEquals("/home", nodes[0].navigationPath)
    }

    @Test
    fun `extracts package from source text`() {
        assertEquals("com.example.feature", extractPackage("package com.example.feature\n\nclass Foo"))
        assertEquals("", extractPackage("class Foo"))
    }

    // --- edge detection ---

    @Test
    fun `detects single-line open call`() {
        val root = fixture()
        write(root, "src/commonMain/kotlin/ui/HomeViewModel.kt",
            "package ui",
            "",
            "class HomeViewModel {",
            "    fun go() { handle.open(SettingsDestination) }",
            "}",
        )
        val config = AtlasConfig(repoRoot = root, outputDir = File(root, "out"),
            sourceRoots = listOf("src/commonMain/kotlin")).resolved()
        val edges = scanEdges(config, setOf("SettingsDestination"))
        assertEquals(1, edges.size)
        assertEquals("SettingsDestination", edges[0].targetDestination)
        assertTrue(edges[0].resolved)
        assertEquals("Home", edges[0].sourceScreen)
    }

    @Test
    fun `detects multi-line open call`() {
        val root = fixture()
        write(root, "src/commonMain/kotlin/ui/HomeViewModel.kt",
            "package ui",
            "",
            "class HomeViewModel {",
            "    fun go() {",
            "        handle.open(",
            "            SettingsDestination",
            "        )",
            "    }",
            "}",
        )
        val config = AtlasConfig(repoRoot = root, outputDir = File(root, "out"),
            sourceRoots = listOf("src/commonMain/kotlin")).resolved()
        val edges = scanEdges(config, setOf("SettingsDestination"))
        assertTrue(edges.any { it.targetDestination == "SettingsDestination" })
    }

    @Test
    fun `filters comment lines`() {
        val root = fixture()
        write(root, "src/commonMain/kotlin/ui/HomeViewModel.kt",
            "package ui",
            "",
            "class HomeViewModel {",
            "    // handle.open(SettingsDestination)",
            "}",
        )
        val config = AtlasConfig(repoRoot = root, outputDir = File(root, "out"),
            sourceRoots = listOf("src/commonMain/kotlin")).resolved()
        val edges = scanEdges(config, setOf("SettingsDestination"))
        assertEquals(0, edges.size)
    }

    @Test
    fun `filters denylisted receivers`() {
        val root = fixture()
        write(root, "src/commonMain/kotlin/ui/HomeViewModel.kt",
            "package ui",
            "",
            "class HomeViewModel {",
            "    fun go() { file.open(SomeDestination) }",
            "}",
        )
        val config = AtlasConfig(repoRoot = root, outputDir = File(root, "out"),
            sourceRoots = listOf("src/commonMain/kotlin")).resolved()
        val edges = scanEdges(config, setOf("SomeDestination"))
        assertEquals(0, edges.size)
    }

    @Test
    fun `marks unknown targets as unresolved`() {
        val root = fixture()
        write(root, "src/commonMain/kotlin/ui/HomeViewModel.kt",
            "package ui",
            "",
            "class HomeViewModel {",
            "    fun go() { handle.open(UnknownDestination) }",
            "}",
        )
        val config = AtlasConfig(repoRoot = root, outputDir = File(root, "out"),
            sourceRoots = listOf("src/commonMain/kotlin")).resolved()
        val edges = scanEdges(config, emptySet())
        assertEquals(1, edges.size)
        assertFalse(edges[0].resolved)
    }

    // --- deriveModuleLabel ---

    @Test
    fun `deriveModuleLabel uses path up to src`() {
        assertEquals("feature:core:client", deriveModuleLabel("feature/core/client/src/commonMain/kotlin/Foo.kt"))
        assertEquals("app:client:common", deriveModuleLabel("app/client/common/src/commonMain/kotlin/Bar.kt"))
    }

    @Test
    fun `deriveModuleLabel falls back to full path when no src`() {
        assertEquals("some/random/path/Foo.kt", deriveModuleLabel("some/random/path/Foo.kt"))
    }

    // --- deriveFeatureGroup ---

    @Test
    fun `deriveFeatureGroup extracts segment after feature from package`() {
        assertEquals("core", deriveFeatureGroup("com.example.feature.core.client.ui", "feature/core/client/src/Foo.kt", emptyList()))
    }

    @Test
    fun `deriveFeatureGroup uses fallbacks when no feature in package`() {
        val fallbacks = listOf("app/admin" to "admin", "app/client" to "app")
        assertEquals("admin", deriveFeatureGroup("com.example.admin.ui", "app/admin/src/Foo.kt", fallbacks))
        assertEquals("app", deriveFeatureGroup("com.example.client.ui", "app/client/src/Foo.kt", fallbacks))
    }

    @Test
    fun `deriveFeatureGroup returns other when no match`() {
        assertEquals("other", deriveFeatureGroup("com.example.random", "random/src/Foo.kt", emptyList()))
    }

    @Test
    fun `deriveFeatureGroup checks fallbacks in order`() {
        val fallbacks = listOf("app" to "app-wide", "app/admin" to "admin")
        assertEquals("app-wide", deriveFeatureGroup("com.example.admin", "app/admin/src/Foo.kt", fallbacks))
    }

    // --- golden scanning ---

    @Test
    fun `reads PNG dimensions from header`() {
        val root = fixture()
        val png = File(root, "test.png")
        writePngHeader(png, 320, 480)
        val (w, h) = readPngDimensions(png)
        assertEquals(320, w)
        assertEquals(480, h)
    }

    @Test
    fun `returns default dimensions for truncated PNG`() {
        val root = fixture()
        val png = File(root, "short.png")
        png.writeBytes(ByteArray(8))
        val (w, h) = readPngDimensions(png)
        assertEquals(800, w)
        assertEquals(800, h)
    }

    private fun writePngHeader(file: File, width: Int, height: Int) {
        val bytes = ByteArray(24)
        // PNG signature
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).copyInto(bytes, 0)
        // IHDR chunk length (13)
        bytes[8] = 0; bytes[9] = 0; bytes[10] = 0; bytes[11] = 13
        // IHDR chunk type
        bytes[12] = 'I'.code.toByte(); bytes[13] = 'H'.code.toByte()
        bytes[14] = 'D'.code.toByte(); bytes[15] = 'R'.code.toByte()
        // Width (big-endian)
        bytes[16] = (width shr 24 and 0xFF).toByte()
        bytes[17] = (width shr 16 and 0xFF).toByte()
        bytes[18] = (width shr 8 and 0xFF).toByte()
        bytes[19] = (width and 0xFF).toByte()
        // Height (big-endian)
        bytes[20] = (height shr 24 and 0xFF).toByte()
        bytes[21] = (height shr 16 and 0xFF).toByte()
        bytes[22] = (height shr 8 and 0xFF).toByte()
        bytes[23] = (height and 0xFF).toByte()
        file.writeBytes(bytes)
    }
}
