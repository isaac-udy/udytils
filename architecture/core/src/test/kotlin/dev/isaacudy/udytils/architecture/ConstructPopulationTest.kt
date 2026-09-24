package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ConstructPopulationTest {

    @Describe("A function that computes a value")
    object Helper : Construct<HelperGroup>(requirements = listOf(isFunction)) {
        @Describe("A Helper must not suspend")
        val notSuspending by rule {
            constrain { decl, _ ->
                val fn = decl as KoFunctionDeclaration
                if (fn.hasSuspendModifier) listOf(Violation(fn, "`${fn.name}` suspends")) else emptyList()
            }
        }
    }

    @Describe("Group with a function construct")
    object HelperGroup : RuleGroup(constructs = listOf(Helper))

    private val sourceDir = File(System.getProperty("user.dir"), "build/tmp/construct-population-scope").apply {
        deleteRecursively()
        mkdirs()
        resolve("Helpers.kt").writeText(
            """
            package sample

            suspend fun topLevel(): Int = 1

            fun outer(): Int {
                suspend fun local(): Int = 2
                return 3
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `construct rules test top-level declarations but not ones local to a function body`() {
        val run = ArchitectureRun(listOf(HelperGroup), scopeProvider = { Konsist.scopeFromExternalDirectory(sourceDir.absolutePath) })
        val rule = run.rules.single { it.id == "HelperGroup.Helper.notSuspending" }
        assertEquals(listOf("`topLevel` suspends"), run.violations(rule).map { it.message })
    }
}
