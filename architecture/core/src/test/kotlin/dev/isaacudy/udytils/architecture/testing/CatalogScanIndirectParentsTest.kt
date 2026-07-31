package dev.isaacudy.udytils.architecture.testing

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [assertCatalogSourcesRegistered] recognizes constructs/groups by scanning the catalog's sources
 * for objects extending `Construct`/`RuleGroup`. With shared rule base classes the extension is
 * indirect (`object UseCase : SidedUseCaseRules<ClientGroup>`), so the scan must resolve parents
 * transitively across files — Konsist's default `parents()` sees only the direct parent and would
 * silently drop every such construct from the declared set.
 */
class CatalogScanIndirectParentsTest {

    private val fixturesDir = File(
        System.getProperty("user.dir"),
        "src/test/kotlin/dev/isaacudy/udytils/architecture/fixtures/sharedrules",
    )

    private fun scope() = run {
        assertTrue(fixturesDir.isDirectory, "fixture directory not found at $fixturesDir")
        Konsist.scopeFromExternalDirectory(fixturesDir.absolutePath)
    }

    @Test
    fun `objects extending a construct base class are recognized only with indirect parents`() {
        val objects = scope().objects()
        val direct = objects
            .filter { obj -> obj.parents().any { it.name.substringBefore('<') == "Construct" } }
            .map { it.name }
        val indirect = objects
            .filter { obj -> obj.parents(indirectParents = true).any { it.name.substringBefore('<') == "Construct" } }
            .map { "${it.packagee?.name}.${it.name}" }
            .toSet()

        assertEquals(emptyList(), direct, "direct-only matching must miss base-class constructs (or this test is stale)")
        assertEquals(
            setOf(
                "dev.isaacudy.udytils.architecture.fixtures.sharedrules.client.UseCase",
                "dev.isaacudy.udytils.architecture.fixtures.sharedrules.server.UseCase",
            ),
            indirect,
        )
    }

    @Test
    fun `objects extending a group base class are recognized only with indirect parents`() {
        val objects = scope().objects()
        val direct = objects
            .filter { obj -> obj.parents().any { it.name.substringBefore('<') == "RuleGroup" } }
            .map { it.name }
        val indirect = objects
            .filter { obj -> obj.parents(indirectParents = true).any { it.name.substringBefore('<') == "RuleGroup" } }
            .map { it.name }
            .toSet()

        assertEquals(emptyList(), direct, "direct-only matching must miss base-class groups (or this test is stale)")
        assertEquals(setOf("ClientGroup", "ServerGroup"), indirect)
    }
}
