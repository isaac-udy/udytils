package dev.isaacudy.udytils.mutation.compiler.operators

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.name.CallableId

/**
 * One kind of code change. The compiler plugin offers each operator the sites of [siteKinds]; the
 * operator returns the changes it makes at the site, or none. Each [Mutation] becomes one mutant,
 * compiled in behind the runtime switch:
 *
 * ```
 * when {
 *     mutantActive(<id>) -> <replacement>
 *     else -> <original>
 * }
 * ```
 */
interface MutationOperator {
    /** The ID the `mutation { }` DSL, the manifests and the report use, for example `remove-call`. */
    val id: String

    /** What the operator changes, in one sentence. */
    val description: String

    val siteKinds: Set<SiteKind>

    /**
     * The mutations of [site]. The plugin calls this before it mutates the site's children, so a
     * replacement built from [Site.copy] contains the original children.
     */
    fun mutations(site: Site): List<Mutation>
}

enum class SiteKind {
    /** The condition of an `if`, a `when` branch, a `while` or a `do-while`. */
    Condition,

    /** The value of a `return` in a declared function; [Site.returnType] is the function's return type. */
    ReturnValue,

    /** Any expression. */
    Expression,

    /** A statement in a block whose value is not used. */
    Statement,
}

/** One mutation: [variant] names the change, for example `< → <=`. */
class Mutation(val variant: String, val replacement: IrExpression)

class Site internal constructor(
    val kind: SiteKind,
    val expression: IrExpression,
    /** The site's source text with whitespace runs collapsed to one space. */
    val text: String,
    val builder: DeclarationIrBuilder,
    val returnType: IrType?,
    private val functions: (CallableId) -> Collection<IrSimpleFunctionSymbol>,
    private val copier: (IrExpression) -> IrExpression,
) {
    val irBuiltIns: IrBuiltIns get() = builder.context.irBuiltIns

    /** A copy of [part] of the site, for use in a replacement. */
    fun copy(part: IrExpression = expression): IrExpression = copier(part)

    fun findFunctions(id: CallableId): Collection<IrSimpleFunctionSymbol> = functions(id)
}
