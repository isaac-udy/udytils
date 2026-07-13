package dev.isaacudy.udytils.rules

import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup
import dev.isaacudy.udytils.architecture.Violation
import dev.isaacudy.udytils.architecture.testing.ArchitectureDocsHarness
import dev.isaacudy.udytils.architecture.testing.architectureGroupNodes
import dev.isaacudy.udytils.architecture.testing.assertCatalogSourcesRegistered
import dev.isaacudy.udytils.architecture.testing.assertEveryGroupHasATestFactory
import dev.isaacudy.udytils.architecture.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/*
 * Hand-written equivalents of the classes the dev.isaacudy.udytils.architecture Gradle plugin
 * generates for a consumer (one @TestFactory per group, the docs harness, and the integrity
 * checks). This repo can't apply the plugin — a build can't apply a plugin produced by one of
 * its own subprojects — so these mirror the generated shape instead.
 */

/** The architecture rules of [UdytilsArchitecture] — one nested test per rule, one factory per group. */
class UdytilsArchitectureTest {

    @TestFactory
    @DisplayName("CoreModule")
    fun coreModule(): List<DynamicNode> = architectureGroupNodes(run, "CoreModule")

    @TestFactory
    @DisplayName("UiModule")
    fun uiModule(): List<DynamicNode> = architectureGroupNodes(run, "UiModule")

    @TestFactory
    @DisplayName("UrpcModules")
    fun urpcModules(): List<DynamicNode> = architectureGroupNodes(run, "UrpcModules")

    @TestFactory
    @DisplayName("PostgresModules")
    fun postgresModules(): List<DynamicNode> = architectureGroupNodes(run, "PostgresModules")

    @TestFactory
    @DisplayName("ArchitectureModules")
    fun architectureModules(): List<DynamicNode> = architectureGroupNodes(run, "ArchitectureModules")

    companion object {
        /** One Konsist scope shared by every factory. */
        private val run by lazy { ArchitectureRun(UdytilsArchitecture) }
    }
}

/** Doc↔catalog sync for [UdytilsArchitecture]'s generated docs. */
class UdytilsArchitectureDocsTest : ArchitectureDocsHarness(UdytilsArchitecture)

/** Self-checks on the architecture-test machinery itself. */
class UdytilsArchitectureIntegrityTest {

    @Test
    fun everyDeclaredConstructAndGroupIsRegistered() = assertCatalogSourcesRegistered(UdytilsArchitecture)

    @Test
    fun everyGroupHasATestFactory() =
        assertEveryGroupHasATestFactory(UdytilsArchitecture, UdytilsArchitectureTest::class)

    /**
     * The violation-detection half of the framework's
     * `assertRunnerDetectsViolationsAndParsesGraph`. The graph half is deliberately not asserted:
     * it requires the module-graph parser to find at least one edge, but this repository declares
     * project dependencies with string notation (`project(":x")`), which the parser does not read
     * yet — it only parses `projects.x.y` typesafe accessors. Restore the framework check if the
     * parser learns string notation (tracked in the catalog README).
     */
    @Test
    fun runnerDetectsViolations() {
        val error = assertFailsWith<Throwable> {
            verify(ArchitectureRun(listOf(TestSentinel), UdytilsArchitecture.scope))
        }
        assertTrue(
            "TestSentinel.alwaysFails" in error.message.orEmpty(),
            "the runner should report the sentinel violation by id; was:\n${error.message}",
        )
    }
}

/** Sentinel group proving a green run means "no violations", not "nothing ran". */
private object TestSentinel : RuleGroup() {
    @Suppress("unused")
    @Describe("Always reports a violation")
    val alwaysFails by rule {
        scope { _, _ -> listOf(Violation("sentinel", "intentional self-check violation")) }
    }
}
