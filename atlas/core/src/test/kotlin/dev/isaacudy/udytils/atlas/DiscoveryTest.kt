package dev.isaacudy.udytils.atlas

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DiscoveryTest {

    private fun fixture(): File {
        val root = kotlin.io.path.createTempDirectory("atlas-discovery").toFile()
        root.deleteOnExit()
        return root
    }

    private fun write(root: File, path: String, content: String = "") {
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `discovers project name from settings_gradle_kts`() {
        val root = fixture()
        write(root, "settings.gradle.kts", """rootProject.name = "my-project"""")
        assertEquals("my-project", AtlasDiscovery.discoverProjectName(root))
    }

    @Test
    fun `discovers project name from settings_gradle`() {
        val root = fixture()
        write(root, "settings.gradle", "rootProject.name = 'groovy-project'")
        assertEquals("groovy-project", AtlasDiscovery.discoverProjectName(root))
    }

    @Test
    fun `falls back to directory name when no settings file`() {
        val root = fixture()
        assertEquals(root.name, AtlasDiscovery.discoverProjectName(root))
    }

    @Test
    fun `discovers source roots`() {
        val root = fixture()
        write(root, "feature/core/client/src/commonMain/kotlin/.marker")
        write(root, "feature/core/server/src/main/kotlin/.marker")
        write(root, "app/client/common/src/commonMain/kotlin/.marker")
        // Directories that should NOT be discovered:
        write(root, "feature/core/client/src/commonTest/kotlin/.marker")
        write(root, "build/generated/src/commonMain/kotlin/.marker")

        val roots = AtlasDiscovery.discoverSourceRoots(root)
        assertTrue(roots.contains("feature/core/client/src/commonMain/kotlin"))
        assertTrue(roots.contains("app/client/common/src/commonMain/kotlin"))
        assertFalse(roots.any { it.contains("build") })
        assertFalse(roots.any { it.contains("Test") })
    }

    @Test
    fun `discovers golden roots`() {
        val root = fixture()
        write(root, "feature/core/client/src/androidHostTest/snapshots/images/.marker")
        val roots = AtlasDiscovery.discoverGoldenRoots(root)
        assertEquals(1, roots.size)
        assertEquals("feature/core/client/src/androidHostTest/snapshots/images", roots[0].path)
        assertEquals("feature:core:client", roots[0].moduleLabel)
    }

    @Test
    fun `prunes submodule paths`() {
        val root = fixture()
        write(root, ".gitmodules", "[submodule \"embedded-lib\"]\n\tpath = embedded-lib\n\turl = git@example.com:lib.git")
        write(root, "embedded-lib/src/commonMain/kotlin/.marker")
        write(root, "feature/core/src/commonMain/kotlin/.marker")

        val roots = AtlasDiscovery.discoverSourceRoots(root)
        assertFalse(roots.any { it.startsWith("embedded-lib") })
        assertTrue(roots.contains("feature/core/src/commonMain/kotlin"))
    }

    @Test
    fun `walk skips dot directories and build`() {
        val root = fixture()
        write(root, ".gradle/src/commonMain/kotlin/.marker")
        write(root, "build/src/commonMain/kotlin/.marker")
        write(root, "node_modules/src/commonMain/kotlin/.marker")
        write(root, "real/src/commonMain/kotlin/.marker")

        val roots = AtlasDiscovery.discoverSourceRoots(root)
        assertEquals(1, roots.size)
        assertEquals("real/src/commonMain/kotlin", roots[0])
    }
}
