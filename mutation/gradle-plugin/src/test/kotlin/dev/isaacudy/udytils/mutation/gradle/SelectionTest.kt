package dev.isaacudy.udytils.mutation.gradle

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionTest {
    private val site = Site(
        id = 7,
        path = "feature/core/server/src/main/kotlin/feature/core/server/domain/Greet.kt",
        module = ":feature:core:server",
        packageName = "feature.core.server.domain",
        line = 16,
        operator = "negate-condition",
        variant = "negated",
        declaration = "feature.core.server.domain.GreetImpl.invoke()",
        text = "history.isEmpty()",
        initScope = false,
        constructs = listOf("ServerDomain.UseCase"),
    )

    @Test
    fun `a construct pattern ending in a star matches every construct of the group`() {
        assertTrue(RuleSpec("domain", constructs = listOf("ServerDomain.*")).selects(site))
        assertFalse(RuleSpec("client", constructs = listOf("ClientDomain.*")).selects(site))
        assertTrue(RuleSpec("exact", constructs = listOf("ServerDomain.UseCase")).selects(site))
    }

    @Test
    fun `every non-empty selector must match`() {
        assertFalse(RuleSpec("both", constructs = listOf("ServerDomain.*"), packages = listOf("feature.other")).selects(site))
        assertTrue(RuleSpec("both", constructs = listOf("ServerDomain.*"), packages = listOf("feature.core.*")).selects(site))
    }

    @Test
    fun `file globs match from the repository root`() {
        assertTrue(RuleSpec("files", files = listOf("feature/**/domain/*.kt")).selects(site))
        assertFalse(RuleSpec("files", files = listOf("feature/*/domain/*.kt")).selects(site))
        assertFalse(RuleSpec("files", files = listOf("**/*.kt"), excludedFiles = listOf("**/Greet.kt")).selects(site))
    }

    @Test
    fun `an operator list limits the rule`() {
        assertFalse(RuleSpec("returns", operators = listOf("boolean-return")).selects(site))
        assertTrue(RuleSpec("conditions", operators = listOf("negate-condition")).selects(site))
    }

    @Test
    fun `an excluded construct removes the site`() {
        assertFalse(RuleSpec("domain", constructs = listOf("ServerDomain.*"), excludedConstructs = listOf("ServerDomain.UseCase")).selects(site))
    }

    @Test
    fun `a call pattern matches the removed function's full name, with a star matching dots`() {
        assertTrue(RuleSpec.matchesCall("org.koin.*", "org.koin.core.module.Module.single"))
        assertTrue(RuleSpec.matchesCall("*.Logger.info", "org.slf4j.Logger.info"))
        assertTrue(RuleSpec.matchesCall("kotlin.collections.MutableList.add", "kotlin.collections.MutableList.add"))
        assertFalse(RuleSpec.matchesCall("kotlin.collections.MutableList.add", "kotlin.collections.MutableList.addAll"))
        assertFalse(RuleSpec.matchesCall("org.koin", "org.koin.core.module.Module.single"))
    }

    @Test
    fun `a rule's excluded calls remove only sites that record a matching callee`() {
        val removal = site.copy(operator = "remove-call", variant = "removed", callee = "org.koin.core.module.Module.single")
        assertFalse(RuleSpec("wiring", excludedCalls = listOf("org.koin.*")).selects(removal))
        assertTrue(RuleSpec("wiring", excludedCalls = listOf("org.slf4j.*")).selects(removal))
        assertTrue(RuleSpec("wiring", excludedCalls = listOf("*")).selects(site))
    }

    @Test
    fun `sampling keeps about the fraction, keeps the same mutants for one seed, and keeps every mutant at 1`() {
        val ids = (1L..20_000L).map { it * 0x9E3779B97F4A7C15uL.toLong() }
        val kept = ids.filter { Sampling.keeps(it, 0.1, seed = 0) }
        assertTrue(kept.size in 1_800..2_200, "kept ${kept.size}")
        assertEquals(kept, ids.filter { Sampling.keeps(it, 0.1, seed = 0) })
        val other = ids.filter { Sampling.keeps(it, 0.1, seed = 1) }
        assertTrue(other.toSet() != kept.toSet())
        assertEquals(ids, ids.filter { Sampling.keeps(it, 1.0, seed = 5) })
    }

    @Test
    fun `a rule survives encoding`() {
        val rule = RuleSpec("domain", constructs = listOf("ServerDomain.*"), operators = listOf("negate-condition"), excludedCalls = listOf("org.koin.*"))
        assertEquals(rule, RuleSpec.decode(rule.encode()))
    }

    @Test
    fun `an expectation accepts a test with one expected tag`() {
        val expectation = Expectation.AnyOf(setOf("subcutaneous", "unit"))
        assertTrue(expectation.accepts(setOf("unit")))
        assertFalse(expectation.accepts(setOf("snapshot")))
        assertTrue(Expectation.Unclassified.accepts(emptySet()))
        assertFalse(Expectation.None("constants").accepts(setOf("unit")))
    }

    @Test
    fun `the innermost declaration supplies the constructs, and expectations combine`() {
        val file = Files.createTempFile("classification", ".json").toFile()
        file.writeText(
            """
            {"categories": [], "constructs": [
              {"id": "A.Outer", "testedBy": {"kind": "anyOf", "categories": ["unit"]}},
              {"id": "A.Inner", "testedBy": {"kind": "anyOf", "categories": ["subcutaneous"]}},
              {"id": "A.Constants", "testedBy": {"kind": "none", "reason": "constants"}}
            ], "declarations": [
              {"path": "a/A.kt", "startLine": 1, "endLine": 50, "constructs": ["A.Outer"]},
              {"path": "a/A.kt", "startLine": 10, "endLine": 20, "constructs": ["A.Inner", "A.Constants"]}
            ]}
            """.trimIndent(),
        )
        val classification = Classification.read(file)
        assertEquals(listOf("A.Inner", "A.Constants"), classification.constructsAt("a/A.kt", 15))
        assertEquals(listOf("A.Outer"), classification.constructsAt("a/A.kt", 30))
        assertEquals(Expectation.AnyOf(setOf("subcutaneous")), classification.expectationOf(listOf("A.Inner", "A.Constants")))
        assertEquals(Expectation.None("constants"), classification.expectationOf(listOf("A.Constants")))
        assertEquals(null, classification.expectationOf(emptyList()))
    }

    @Test
    fun `manifests join each site to its module and drop deleted source files`() {
        val repository = Files.createTempDirectory("repository").toFile()
        val source = File(repository, "feature/core/server/src/main/kotlin/Greet.kt").apply { parentFile.mkdirs(); writeText("") }
        val manifests = File(repository, "manifests").apply { mkdirs() }
        File(manifests, "a.json").writeText(
            """{"file": ${quote(source.absolutePath)}, "package": "feature.core", "mutants": [
                {"id": 42, "line": 3, "operator": "negate-condition", "declaration": "f()", "text": "x", "initScope": false},
                {"id": 44, "line": 4, "operator": "remove-call", "variant": "removed", "declaration": "f()", "text": "log.info(x)", "initScope": false, "callee": "org.slf4j.Logger.info"}]}""",
        )
        File(manifests, "b.json").writeText(
            """{"file": ${quote(File(repository, "Deleted.kt").absolutePath)}, "package": "p", "mutants": [
                {"id": 43, "line": 1, "operator": "negate-condition", "declaration": "g()", "text": "y", "initScope": true}]}""",
        )
        val sites = Manifests.read(listOf(manifests), repository, mapOf(":feature:core:server" to "feature/core/server", ":feature:core" to "feature/core"))
        assertEquals(listOf(42L, 44L), sites.map { it.id })
        assertEquals(":feature:core:server", sites.first().module)
        assertEquals("feature/core/server/src/main/kotlin/Greet.kt", sites.first().path)
        assertEquals(listOf(null, "org.slf4j.Logger.info"), sites.map { it.callee })
    }

    private fun quote(path: String) = "\"" + path.replace("\\", "\\\\") + "\""
}
