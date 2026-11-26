package dev.isaacudy.udytils.mutation.compiler.operators

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin

/**
 * Negates a condition.
 *
 * `if (history.isEmpty())` → `if (!history.isEmpty())`; also `when` branch, `while` and
 * `do-while` conditions.
 */
object NegateCondition : MutationOperator {
    override val id = "negate-condition"
    override val description = "Negates the condition of an if, a when branch, a while or a do-while."
    override val siteKinds = setOf(SiteKind.Condition)

    override fun mutations(site: Site): List<Mutation> {
        val not = site.builder.irCall(site.irBuiltIns.booleanNotSymbol, site.irBuiltIns.booleanType, origin = IrStatementOrigin.EXCL).apply {
            arguments[0] = site.copy()
        }
        return listOf(Mutation("negated", not))
    }
}
