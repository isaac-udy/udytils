package dev.isaacudy.udytils.rules

import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.testing.ArchitectureDocsHarness
import dev.isaacudy.udytils.architecture.testing.architectureGroupNodes
import dev.isaacudy.udytils.architecture.testing.assertCatalogSourcesRegistered
import dev.isaacudy.udytils.architecture.testing.assertEveryGroupHasATestFactory
import dev.isaacudy.udytils.architecture.testing.assertRunnerDetectsViolationsAndParsesGraph
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test

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

    @Test
    fun runnerDetectsViolationsAndParsesGraph() = assertRunnerDetectsViolationsAndParsesGraph(UdytilsArchitecture)
}
