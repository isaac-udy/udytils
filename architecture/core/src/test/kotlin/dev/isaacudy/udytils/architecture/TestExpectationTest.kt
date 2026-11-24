package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.Konsist
import dev.isaacudy.udytils.architecture.docs.renderConstructBlock
import dev.isaacudy.udytils.architecture.testing.assertTestExpectationsDeclared
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestExpectationTest {

    object Categories {
        val Unit = TestCategory("unit", "Unit")
        val Snapshot = TestCategory("snapshot", "Snapshot")
    }

    @Describe("A class named `[Name]Model`")
    object Model : Construct<SampleGroup>(
        requirements = listOf(isClassWhere("is named `[Name]Model`") { it.name.endsWith("Model") }),
        testedBy = TestExpectation.anyOf(Categories.Unit, Categories.Snapshot),
    )

    @Describe("A function named `make[Name]`")
    object Factory : Construct<SampleGroup>(
        requirements = listOf(isFunction),
        testedBy = TestExpectation.none("a factory only calls a constructor"),
    )

    @Describe("An object")
    object Holder : Construct<SampleGroup>(requirements = listOf(isObject))

    @Describe("Sample group")
    object SampleGroup : RuleGroup(constructs = listOf(Model, Factory, Holder))

    private val sourceDir = File(System.getProperty("user.dir"), "build/tmp/test-expectation-scope").apply {
        deleteRecursively()
        mkdirs()
        resolve("Sample.kt").writeText(
            """
            package sample

            /** A model. */
            @Suppress("unused")
            class UserModel(
                val name: String,
            )

            fun makeUser(): UserModel = UserModel("a")

            class Unmatched
            """.trimIndent(),
        )
    }

    private fun run() = ArchitectureRun(listOf(SampleGroup), scopeProvider = { Konsist.scopeFromExternalDirectory(sourceDir.absolutePath) })

    @Test
    fun `classify reports each declaration's line range and matching constructs`() {
        val declarations = classify(run())

        assertEquals(
            listOf(
                Triple("sample.UserModel", 3..7, listOf("SampleGroup.Model")),
                Triple("sample.makeUser", 9..9, listOf("SampleGroup.Factory")),
                Triple("sample.Unmatched", 11..11, emptyList()),
            ),
            declarations.map { Triple(it.name, it.startLine..it.endLine, it.constructs.map(Construct<*>::id)) },
        )
        assertTrue(declarations.all { it.path.endsWith("build/tmp/test-expectation-scope/Sample.kt") && !it.path.startsWith("/") })
        assertTrue(declarations.all { it.codeScope == CodeScope.Production })
    }

    @Test
    fun `the classification JSON lists categories, construct expectations and declarations`() {
        val json = renderClassificationJson(listOf(SampleGroup), classify(run()))

        assertTrue(json.contains("""{"id": "unit", "name": "Unit"}"""))
        assertTrue(json.contains("""{"id": "snapshot", "name": "Snapshot"}"""))
        assertTrue(json.contains(""""id": "SampleGroup.Model", "codeScope": "production", "testedBy": {"kind": "anyOf", "categories": ["unit", "snapshot"]}"""))
        assertTrue(json.contains(""""testedBy": {"kind": "none", "reason": "a factory only calls a constructor"}"""))
        assertTrue(json.contains(""""id": "SampleGroup.Holder", "codeScope": "production", "testedBy": {"kind": "unspecified"}"""))
        assertTrue(json.contains(""""startLine": 3, "endLine": 7, "name": "sample.UserModel", "codeScope": "production", "constructs": ["SampleGroup.Model"]"""))
        assertTrue(json.contains(""""name": "sample.Unmatched", "codeScope": "production", "constructs": []"""))
    }

    @Test
    fun `construct docs state the test expectation`() {
        run()
        assertTrue(renderConstructBlock(Model).contains("##### Tested by\n\n* Unit or Snapshot\n"))
        assertTrue(renderConstructBlock(Factory).contains("##### Tested by\n\n* No tests are expected: a factory only calls a constructor\n"))
        assertFalse(renderConstructBlock(Holder).contains("Tested by"))
    }

    @Test
    fun `a definition that requires expectations fails for an unspecified construct`() {
        val required = object : ArchitectureDefinition(
            groups = listOf(SampleGroup),
            scope = { Konsist.scopeFromExternalDirectory(sourceDir.absolutePath) },
            docs = DocsConfig(module = "sample"),
            requireTestExpectations = true,
        ) {}
        val failure = assertFailsWith<AssertionError> { assertTestExpectationsDeclared(required) }
        assertTrue(failure.message!!.contains("SampleGroup.Holder"))
        assertFalse(failure.message!!.contains("SampleGroup.Model"))

        val optional = object : ArchitectureDefinition(
            groups = listOf(SampleGroup),
            scope = { Konsist.scopeFromExternalDirectory(sourceDir.absolutePath) },
            docs = DocsConfig(module = "sample"),
        ) {}
        assertTestExpectationsDeclared(optional)
    }

    @Test
    fun `expectations reject malformed declarations`() {
        assertFailsWith<IllegalArgumentException> { TestCategory("Unit Tests", "Unit") }
        assertFailsWith<IllegalArgumentException> { TestExpectation.anyOf() }
        assertFailsWith<IllegalArgumentException> { TestExpectation.anyOf(Categories.Unit, Categories.Unit) }
        assertFailsWith<IllegalArgumentException> { TestExpectation.none(" ") }
    }
}
