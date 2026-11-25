package dev.isaacudy.udytils.mutation.compiler.operators

import org.jetbrains.kotlin.ir.builders.irFalse
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrWhen

/**
 * Changes a `&&` or `||` expression: swaps the connector, and keeps only one operand.
 *
 * `a && b` → `a || b`, `a`, and `b`; `a || b` → `a && b`, `a`, and `b`.
 */
object LogicalConnector : MutationOperator {
    override val id = "logical-connector"
    override val description = "Swaps && and ||, and replaces the expression with each operand alone."
    override val siteKinds = setOf(SiteKind.Expression)

    override fun mutations(site: Site): List<Mutation> {
        val expression = site.expression as? IrWhen ?: return emptyList()
        val and = when (expression.origin) {
            IrStatementOrigin.ANDAND -> true
            IrStatementOrigin.OROR -> false
            else -> return emptyList()
        }
        if ((if (and) "&&" else "||") !in site.text) return emptyList()
        val left = expression.branches[0].condition
        val right = if (and) expression.branches[0].result else expression.branches[1].result
        val b = site.builder
        val boolean = site.irBuiltIns.booleanType
        val swapped = if (and) {
            b.irIfThenElse(boolean, site.copy(left), b.irTrue(), site.copy(right), IrStatementOrigin.OROR)
        } else {
            b.irIfThenElse(boolean, site.copy(left), site.copy(right), b.irFalse(), IrStatementOrigin.ANDAND)
        }
        return listOf(
            Mutation(if (and) "&& → ||" else "|| → &&", swapped),
            Mutation("left operand only", site.copy(left)),
            Mutation("right operand only", site.copy(right)),
        )
    }
}
