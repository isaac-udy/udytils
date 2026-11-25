package dev.isaacudy.udytils.mutation.compiler.operators

import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrWhen

/**
 * Replaces an elvis expression with its right side: `a ?: b` → `b`.
 *
 * `cached ?: load()` → `load()`; `user ?: return` → `return`.
 */
object Elvis : MutationOperator {
    override val id = "elvis"
    override val description = "Replaces a ?: b with b."
    override val siteKinds = setOf(SiteKind.Expression)

    override fun mutations(site: Site): List<Mutation> {
        val block = site.expression as? IrBlock ?: return emptyList()
        if (block.origin != IrStatementOrigin.ELVIS || "?:" !in site.text) return emptyList()
        val right = (block.statements.lastOrNull() as? IrWhen)?.branches?.firstOrNull()?.result ?: return emptyList()
        return listOf(Mutation("→ right side", site.copy(right)))
    }
}
