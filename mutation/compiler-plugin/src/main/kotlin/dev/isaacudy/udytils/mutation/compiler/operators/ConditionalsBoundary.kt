package dev.isaacudy.udytils.mutation.compiler.operators

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol

/**
 * Moves the boundary of a comparison: `<` ↔ `<=` and `>` ↔ `>=`.
 *
 * `if (attempts < limit)` → `if (attempts <= limit)`. Applies to primitive comparisons and to
 * `Comparable.compareTo` comparisons.
 */
object ConditionalsBoundary : MutationOperator {
    override val id = "conditionals-boundary"
    override val description = "Moves the boundary of a comparison: < and <=, > and >= swap."
    override val siteKinds = setOf(SiteKind.Expression)

    private enum class Comparison(val token: String) { Less("<"), LessOrEqual("<="), Greater(">"), GreaterOrEqual(">=") }

    private val swaps = mapOf(
        Comparison.Less to Comparison.LessOrEqual, Comparison.LessOrEqual to Comparison.Less,
        Comparison.Greater to Comparison.GreaterOrEqual, Comparison.GreaterOrEqual to Comparison.Greater,
    )

    override fun mutations(site: Site): List<Mutation> {
        val call = site.expression as? IrCall ?: return emptyList()
        val tables = mapOf(
            Comparison.Less to site.irBuiltIns.lessFunByOperandType,
            Comparison.LessOrEqual to site.irBuiltIns.lessOrEqualFunByOperandType,
            Comparison.Greater to site.irBuiltIns.greaterFunByOperandType,
            Comparison.GreaterOrEqual to site.irBuiltIns.greaterOrEqualFunByOperandType,
        )
        val (comparison, operand) = tables.firstNotNullOfOrNull { (comparison, table) ->
            table.operandOf(call.symbol)?.let { comparison to it }
        } ?: return emptyList()
        if (comparison.token !in site.text) return emptyList()
        val target = swaps.getValue(comparison)
        val function = tables.getValue(target)[operand] ?: return emptyList()
        val replacement = site.builder.irCall(function).apply {
            arguments[0] = site.copy(call.arguments[0]!!)
            arguments[1] = site.copy(call.arguments[1]!!)
        }
        return listOf(Mutation("${comparison.token} → ${target.token}", replacement))
    }

    private fun Map<IrClassifierSymbol, IrSimpleFunctionSymbol>.operandOf(symbol: IrSimpleFunctionSymbol) =
        entries.firstOrNull { it.value == symbol }?.key
}
