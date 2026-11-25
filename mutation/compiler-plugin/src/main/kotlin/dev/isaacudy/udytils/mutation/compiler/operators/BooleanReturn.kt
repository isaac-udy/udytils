package dev.isaacudy.udytils.mutation.compiler.operators

import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.types.isBoolean

/**
 * Replaces the value returned by a function that returns `Boolean` with `true`, and with `false`.
 *
 * `return name.isNotBlank()` → `return true` and `return false`. A variant equal to a literal
 * return value is skipped.
 */
object BooleanReturn : MutationOperator {
    override val id = "boolean-return"
    override val description = "Replaces the value a Boolean function returns with true, and with false."
    override val siteKinds = setOf(SiteKind.ReturnValue)

    override fun mutations(site: Site): List<Mutation> {
        if (site.returnType?.isBoolean() != true) return emptyList()
        val literal = (site.expression as? IrConst)?.value
        return listOf(true, false).filter { it != literal }.map { Mutation("→ $it", site.builder.irBoolean(it)) }
    }
}
