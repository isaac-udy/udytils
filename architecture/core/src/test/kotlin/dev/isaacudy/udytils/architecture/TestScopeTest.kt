package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestScopeTest {

    @Describe("A class named `*Repository`")
    object Repository : Construct<ProductionGroup>(requirements = listOf(isClass, hasNameEndingWith("Repository")))

    @Describe("A class named `*Model`")
    object Model : Construct<ProductionGroup>(requirements = listOf(isClass, hasNameEndingWith("Model")))

    @Describe("Production code")
    object ProductionGroup : RuleGroup(inPackage = "sample..", constructs = listOf(Repository, Model))

    @Describe("A class named `*Test`")
    object SampleTest : Construct<TestGroup>(requirements = listOf(isClass, hasNameEndingWith("Test"))) {
        @Describe("A test references only Models")
        val referencesModelsOnly by rule {
            constrainWithScopes { decl, scopes, _ ->
                val file = (decl as KoContainingFileProvider).containingFile
                scopes.productionReferences(file)
                    .filterNot { reference -> Model in reference.constructs }
                    .map { Violation(decl, "references ${it.name}") }
            }
        }
    }

    @Describe("Test code")
    object TestGroup : RuleGroup(inPackage = "sample..", constructs = listOf(SampleTest), codeScope = CodeScope.Test)

    private val root = File(System.getProperty("user.dir"), "build/tmp/test-scope").apply {
        deleteRecursively()
        resolve("main/sample").mkdirs()
        resolve("test/sample/other").mkdirs()
        resolve("main/sample/Production.kt").writeText(
            """
            package sample

            class GreetingRepository
            class GreetingModel
            """.trimIndent(),
        )
        resolve("test/sample/SamePackageTest.kt").writeText(
            """
            package sample

            class SamePackageTest {
                val model = GreetingModel()
                val repository = GreetingRepository()
            }
            """.trimIndent(),
        )
        resolve("test/sample/other/ImportingTest.kt").writeText(
            """
            package sample.other

            import sample.GreetingModel
            import sample.*

            // Mentions GreetingRepository only in a comment.
            class ImportingTest {
                val model = GreetingModel()
                val label = "GreetingRepository"
            }

            class UnclassifiedHelper
            """.trimIndent(),
        )
    }

    private fun run(withTestScope: Boolean = true) = ArchitectureRun(
        groups = listOf(ProductionGroup, TestGroup),
        scopeProvider = { Konsist.scopeFromExternalDirectory(root.resolve("main").absolutePath) },
        membership = { true },
        testScopeProvider = if (withTestScope) {
            { Konsist.scopeFromExternalDirectory(root.resolve("test").absolutePath) }
        } else {
            null
        },
        testMembership = { true },
    )

    private fun ArchitectureRun.violationsOf(id: String) = violations(rules.single { it.id == id })

    private fun List<Violation>.names() = map { it.where.substringAfterLast('/').substringBefore(':') }.sorted()

    @Test
    fun `production exhaustiveness never sees test declarations`() {
        assertEquals(emptyList(), run().violationsOf("ProductionGroup.exhaustive"))
        assertEquals(emptyList(), run().violationsOf("architecture.everyDeclarationBelongsToALayer"))
    }

    @Test
    fun `test exhaustiveness classifies test declarations by test constructs only`() {
        assertEquals(listOf("ImportingTest.kt"), run().violationsOf("TestGroup.exhaustive").names())
    }

    @Test
    fun `test membership covers the test scope`() {
        assertEquals(
            listOf("ImportingTest.kt"),
            run().violationsOf("architecture.everyTestDeclarationBelongsToATestConstruct").names(),
        )
    }

    @Test
    fun `production references resolve imports and same-package names to production constructs`() {
        val violations = run().violationsOf("TestGroup.SampleTest.referencesModelsOnly")
        assertEquals(listOf("references GreetingRepository"), violations.map { it.message })
        assertEquals(listOf("SamePackageTest.kt"), violations.names())
    }

    @Test
    fun `a test-scoped rule without a test scope fails naming the missing scope`() {
        val run = run(withTestScope = false)
        assertEquals(emptyList(), run.violationsOf("ProductionGroup.exhaustive"))
        val error = assertFailsWith<IllegalArgumentException> { run.violationsOf("TestGroup.exhaustive") }
        assertTrue("testScope" in error.message.orEmpty())
    }

    @Test
    fun `constructsOf classifies against production constructs`() {
        val scopes = run().scopes
        val repository: KoBaseDeclaration = scopes.production.classes().single { it.name == "GreetingRepository" }
        assertEquals(listOf<Construct<*>>(Repository), scopes.constructsOf(repository))
        assertTrue(scopes.tests.classes().none { it.name == "GreetingRepository" })
        assertTrue(scopes.tests.classes().all { it is KoClassDeclaration })
    }
}
