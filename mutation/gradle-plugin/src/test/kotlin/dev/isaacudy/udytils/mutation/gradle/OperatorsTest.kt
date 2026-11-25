package dev.isaacudy.udytils.mutation.gradle

import dev.isaacudy.udytils.mutation.compiler.operators.MutationOperators
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OperatorsTest {
    @Test
    fun `the plugin's operator ids equal the compiler plugin's operators`() {
        assertEquals(MutationOperators.all.map { it.id }, Operators.all)
        assertEquals(MutationOperators.defaults.map { it.id }, Operators.defaults)
    }

    @Test
    fun `a misspelt operator in the mutation block fails with the known operators`() {
        val extension = ProjectBuilder.builder().build().also { it.pluginManager.apply(MutationPlugin::class.java) }
            .extensions.getByType(MutationExtension::class.java)
        val failure = assertFailsWith<IllegalArgumentException> { extension.operators("negate-condition", "remov-call") }
        assertTrue("\"remov-call\"" in failure.message!! && "remove-call" in failure.message!!, failure.message)
        assertEquals(Operators.defaults.toSet(), extension.operators.get())
    }

    @Test
    fun `a misspelt operator in a rule fails with the rule id`() {
        val failure = assertFailsWith<IllegalArgumentException> { MutationRule("services").operators("remov-call") }
        assertTrue("rule(\"services\")" in failure.message!!, failure.message)
    }
}
