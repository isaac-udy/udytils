package dev.isaacudy.udytils.architecture.testing

import dev.isaacudy.udytils.architecture.ArchitectureDefinition
import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup
import dev.isaacudy.udytils.architecture.Violation
import dev.isaacudy.udytils.architecture.verify
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Proves the runner actually detects and reports violations (so a green architecture run means
 * "no violations", not "nothing ran"), and that the module-graph provider parses real edges (so
 * module rules aren't passing vacuously). Runs a sentinel catalog against the definition's scope.
 */
fun assertRunnerDetectsViolationsAndParsesGraph(definition: ArchitectureDefinition) {
    val error = assertFailsWith<Throwable> {
        verify(ArchitectureRun(listOf(SentinelRules), definition.scope))
    }
    val message = error.message.orEmpty()
    assertTrue("SentinelRules.alwaysFails" in message, "the runner should report the violation by id; was:\n$message")
    assertTrue("SentinelRules.graphParses" !in message, "the module graph should parse real edges; was:\n$message")
}

/** Sentinel group for [assertRunnerDetectsViolationsAndParsesGraph]. */
private object SentinelRules : RuleGroup() {
    @Suppress("unused")
    @Describe("Always reports a violation")
    val alwaysFails by rule {
        scope { _, _ -> listOf(Violation("sentinel", "intentional self-check violation")) }
    }

    @Suppress("unused")
    @Describe("The module graph parses at least one module edge")
    val graphParses by rule {
        moduleGraph { graph, _ ->
            if (graph.edges.isEmpty()) listOf(Violation("graph", "no module edges were parsed")) else emptyList()
        }
    }
}
