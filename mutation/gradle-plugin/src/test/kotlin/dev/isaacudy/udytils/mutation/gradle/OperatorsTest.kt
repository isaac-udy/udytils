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

    @Test
    fun `a rule or run selection that names an operator the build does not compile in fails`() {
        val compiled = setOf("negate-condition")
        val rule = RuleSpec("services", operators = listOf("remove-call"))
        val ruleFailure = assertFailsWith<IllegalArgumentException> { Operators.checkSelection(compiled, listOf(rule), emptyList()) }
        assertTrue("rule(\"services\")" in ruleFailure.message!! && "does not compile in" in ruleFailure.message!!, ruleFailure.message)
        val runFailure = assertFailsWith<IllegalArgumentException> { Operators.checkSelection(compiled, emptyList(), listOf("elvis")) }
        assertTrue("-Pmutation.operators" in runFailure.message!!, runFailure.message)
        Operators.checkSelection(compiled, emptyList(), listOf("negate-condition"))
    }

    @Test
    fun `a misspelt operator set directly on the extension property fails the selection check`() {
        val failure = assertFailsWith<IllegalArgumentException> { Operators.checkSelection(setOf("remov-call"), emptyList(), emptyList()) }
        assertTrue("\"remov-call\"" in failure.message!!, failure.message)
    }

    @Test
    fun `a misspelt or uncompiled sampled operator and a fraction outside the range fail`() {
        val extension = ProjectBuilder.builder().build().also { it.pluginManager.apply(MutationPlugin::class.java) }
            .extensions.getByType(MutationExtension::class.java)
        val misspelt = assertFailsWith<IllegalArgumentException> { extension.sample("remov-call", 0.1) }
        assertTrue("\"remov-call\"" in misspelt.message!!, misspelt.message)
        assertFailsWith<IllegalArgumentException> { extension.sample("remove-call", 0.0) }
        assertFailsWith<IllegalArgumentException> { extension.sample("remove-call", 1.5) }
        val uncompiled = assertFailsWith<IllegalArgumentException> {
            Operators.checkSelection(setOf("negate-condition"), emptyList(), emptyList(), mapOf("remove-call" to 0.1))
        }
        assertTrue("does not compile in" in uncompiled.message!!, uncompiled.message)
        val property = Sampling.parse("remove-call=0.1, elvis=1", "-Pmutation.sample")
        assertEquals(mapOf("remove-call" to 0.1, "elvis" to 1.0), property)
        assertFailsWith<IllegalArgumentException> { Sampling.parse("remove-call:0.1", "-Pmutation.sample") }
        assertFailsWith<IllegalArgumentException> {
            Operators.checkSelection(Operators.all.toSet(), emptyList(), emptyList(), Sampling.parse("remov-call=0.1", "-Pmutation.sample"))
        }
    }
}
