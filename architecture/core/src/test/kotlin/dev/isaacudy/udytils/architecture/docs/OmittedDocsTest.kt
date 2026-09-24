package dev.isaacudy.udytils.architecture.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OmittedDocsTest {

    private val moduleRoot = File("build/omitted-docs-test-missing")

    @Test
    fun `links into an omitted doc render as their text`() {
        val errors = mutableListOf<String>()
        val docs = listOf(
            GeneratedDoc("docs/server.md", "See the [Repository](client.md#repository) and [Rules](server.md#rules).\n\n## Rules"),
            GeneratedDoc("README.md", "Start at [client](docs/client.md)."),
        )

        val result = unlinkOmittedDocs(docs, setOf("docs/client.md"), errors)
        validateLinks(result, moduleRoot, errors)

        assertEquals(emptyList(), errors)
        assertEquals("See the Repository and [Rules](server.md#rules).\n\n## Rules", result[0].content)
        assertEquals("Start at client.", result[1].content)
    }

    @Test
    fun `links inside code fences are left alone`() {
        val content = "```\n[Repository](client.md)\n```"
        val result = unlinkOmittedDocs(listOf(GeneratedDoc("docs/server.md", content)), setOf("docs/client.md"), mutableListOf())
        assertEquals(content, result.single().content)
    }

    @Test
    fun `other broken links still fail`() {
        val errors = mutableListOf<String>()
        val docs = listOf(GeneratedDoc("docs/server.md", "See [Missing](missing.md)."))

        validateLinks(unlinkOmittedDocs(docs, setOf("docs/client.md"), errors), moduleRoot, errors)

        assertEquals(listOf("docs/server.md: broken link `missing.md`"), errors)
    }

    @Test
    fun `omitting a generated doc is reported`() {
        val errors = mutableListOf<String>()
        unlinkOmittedDocs(listOf(GeneratedDoc("docs/client.md", "# Client")), setOf("docs/client.md"), errors)
        assertTrue(errors.single().contains("docs/client.md"))
    }
}
