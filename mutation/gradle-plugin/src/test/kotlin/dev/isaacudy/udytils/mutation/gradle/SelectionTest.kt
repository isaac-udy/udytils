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
        assertFalse(RuleSpec("returns", operators = listOf("negate-boolean-return")).selects(site))
        assertTrue(RuleSpec("conditions", operators = listOf("negate-condition")).selects(site))
    }

    @Test
    fun `an excluded construct removes the site`() {
        assertFalse(RuleSpec("domain", constructs = listOf("ServerDomain.*"), excludedConstructs = listOf("ServerDomain.UseCase")).selects(site))
    }

    @Test
    fun `a rule survives encoding`() {
        val rule = RuleSpec("domain", constructs = listOf("ServerDomain.*"), operators = listOf("negate-condition"))
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
                {"id": 42, "line": 3, "operator": "negate-condition", "declaration": "f()", "text": "x", "initScope": false}]}""",
        )
        File(manifests, "b.json").writeText(
            """{"file": ${quote(File(repository, "Deleted.kt").absolutePath)}, "package": "p", "mutants": [
                {"id": 43, "line": 1, "operator": "negate-condition", "declaration": "g()", "text": "y", "initScope": true}]}""",
        )
        val sites = Manifests.read(listOf(manifests), repository, mapOf(":feature:core:server" to "feature/core/server", ":feature:core" to "feature/core"))
        assertEquals(1, sites.size)
        assertEquals(":feature:core:server", sites.single().module)
        assertEquals("feature/core/server/src/main/kotlin/Greet.kt", sites.single().path)
    }

    private fun quote(path: String) = "\"" + path.replace("\\", "\\\\") + "\""
}
