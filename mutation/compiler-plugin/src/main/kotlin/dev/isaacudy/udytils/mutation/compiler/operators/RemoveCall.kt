package dev.isaacudy.udytils.mutation.compiler.operators

import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.util.isSetter
import org.jetbrains.kotlin.ir.util.kotlinFqName

/**
 * Removes a call whose result is not used.
 *
 * `repository.save(greeting)` → (nothing); also property assignments (`state.value = loaded`),
 * compound assignments (`items += item`, `count += 1`) and increments (`count++`) of a property. A test that never checks a call's effect lets this
 * mutant survive.
 *
 * Skipped: calls that return `Nothing` (`error()`, `fail()`), `contract { }`, and calls to the
 * mutation runtime.
 */
object RemoveCall : MutationOperator {
    override val id = "remove-call"
    override val description = "Removes a call whose result is not used, including property and compound assignments."
    override val siteKinds = setOf(SiteKind.Statement)

    private val origins = setOf(
        null, IrStatementOrigin.EQ, IrStatementOrigin.PLUSEQ, IrStatementOrigin.MINUSEQ,
        IrStatementOrigin.MULTEQ, IrStatementOrigin.DIVEQ, IrStatementOrigin.PERCEQ,
    )
    /** A compound assignment or increment of a property compiles to a block that calls the setter. */
    private val compoundAssignments = setOf(
        IrStatementOrigin.PLUSEQ, IrStatementOrigin.MINUSEQ, IrStatementOrigin.MULTEQ, IrStatementOrigin.DIVEQ, IrStatementOrigin.PERCEQ,
        IrStatementOrigin.PREFIX_INCR, IrStatementOrigin.PREFIX_DECR, IrStatementOrigin.POSTFIX_INCR, IrStatementOrigin.POSTFIX_DECR,
    )
    private val skipped = setOf("kotlin.contracts.contract")
    private const val RUNTIME_PACKAGE = "dev.isaacudy.udytils.mutation."

    override fun mutations(site: Site): List<Mutation> {
        val statement = site.expression
        if (statement is IrBlock && statement.origin in compoundAssignments && statement.statements.any { (it as? IrCall)?.symbol?.owner?.isSetter == true }) {
            return listOf(Mutation("removed", site.builder.irUnit()))
        }
        val call = (statement as? IrTypeOperatorCall)
            ?.takeIf { it.operator == IrTypeOperator.IMPLICIT_COERCION_TO_UNIT }?.argument as? IrCall
            ?: statement as? IrCall
            ?: return emptyList()
        if (call.origin !in origins || call.type.isNothing()) return emptyList()
        val name = call.symbol.owner.kotlinFqName.asString()
        if (name in skipped || name.startsWith(RUNTIME_PACKAGE)) return emptyList()
        return listOf(Mutation("removed", site.builder.irUnit()))
    }
}
