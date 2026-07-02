package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingDeclarationProvider
import com.lemonappdev.konsist.api.provider.modifier.KoModifierProvider

/** Every top-level declaration in a layer's package must match exactly one of its constructs. */
internal fun exhaustiveRule(group: RuleGroup): Rule = Rule(
    id = "${group.id}.exhaustive",
    title = "Every top-level declaration in `${group.inPackage}` must match exactly one Construct",
    rationale = """
        A declaration that matches no Construct (or more than one) is either misplaced or a shape the
        architecture doesn't recognise; make it conform to an existing Construct, or add a new one.
    """.trimIndent(),
    enforcement = ScopeConstraint(membershipCheck(group.constructs, universe = { it.residesIn(group.inPackage!!) })),
    status = Status.Active,
    notes = emptyList(),
)

/**
 * Cross-layer: every declaration in the [universe] (the consumer's "governed code" predicate from
 * [ArchitectureDefinition.membership]) must match exactly one construct across all layers.
 */
internal fun membershipRule(groups: List<RuleGroup>, universe: (KoBaseDeclaration) -> Boolean): Rule = Rule(
    id = "architecture.everyDeclarationBelongsToALayer",
    title = "Every declaration in governed code must match exactly one Construct across all RuleGroups",
    rationale = """
        A declaration that matches no Construct (or more than one) is either misplaced or a shape the
        architecture doesn't recognise. This Rule covers declarations that aren't in any single
        RuleGroup's package, such as a feature's DI module.
    """.trimIndent(),
    enforcement = ScopeConstraint(membershipCheck(groups.flatMap { it.constructs }, universe)),
    status = Status.Active,
    notes = emptyList(),
)

/**
 * Shared classification check: every classifiable declaration in the [universe] must match exactly
 * one of [constructs]; partial matches get a rich breakdown.
 */
private fun membershipCheck(constructs: List<Construct<*>>, universe: (KoBaseDeclaration) -> Boolean): ScopeCheck =
    ScopeCheck { scope, exempt ->
        classifiableDeclarations(scope)
            .filter(universe)
            .filterNot { exempt(it) || ArchitectureExceptions.isIgnored(it) }
            .filter { decl -> constructs.count { it.test(decl) } != 1 }
            .map { Violation(it, classifyMessage(it, constructs)) }
    }

private fun classifiableDeclarations(scope: KoScope): List<KoBaseDeclaration> =
    scope.declarations(includeNested = false)
        .filter {
            it is KoClassDeclaration || it is KoInterfaceDeclaration || it is KoObjectDeclaration ||
                it is KoFunctionDeclaration || it is KoPropertyDeclaration
        }
        .filterNot { (it as? KoModifierProvider)?.hasModifier(KoModifier.PRIVATE) == true }
        .filterNot { (it as? KoContainingDeclarationProvider)?.containingDeclaration is KoFunctionDeclaration }

/** Human breakdown for a declaration that matched no construct (or several). */
private fun classifyMessage(declaration: KoBaseDeclaration, constructs: List<Construct<*>>): String {
    val location = declaration.sourceLocation()
    val matched = constructs.filter { it.test(declaration) }
    return when {
        matched.size > 1 -> "$location matches multiple Constructs: ${matched.joinToString { it.id }}"
        else -> buildString {
            val scored = constructs
                .map { c -> c to c.requirements.count { it.matches(declaration) }.toDouble() / c.requirements.size }
                .filter { it.second > 0.0 }
            val best = scored.maxOfOrNull { it.second } ?: 0.0
            val closest = scored.filter { best - it.second < 0.15 }.sortedByDescending { it.second }
            append("$location matches no Construct")
            if (closest.isNotEmpty()) {
                appendLine("; closest:")
                closest.forEach { (construct, pct) ->
                    appendLine("    ${construct.id} (${(pct * 100).toInt()}%)")
                    construct.requirements.forEach { req ->
                        appendLine("        [${if (req.matches(declaration)) "✓" else " "}] ${req.description}")
                    }
                }
            }
        }
    }
}
