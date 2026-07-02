package dev.isaacudy.udytils.architecture.docs

import dev.isaacudy.udytils.architecture.ArchitectureDefinition
import dev.isaacudy.udytils.architecture.Construct
import dev.isaacudy.udytils.architecture.Rule
import dev.isaacudy.udytils.architecture.RuleGroup
import dev.isaacudy.udytils.architecture.exhaustiveRule
import dev.isaacudy.udytils.architecture.membershipRule
import dev.isaacudy.udytils.architecture.prepare

/**
 * Id-indexed view of the catalog for doc generation: groups, constructs, and every rule the engine
 * enforces (including the derived exhaustiveness rules and, when the definition provides a
 * membership universe, the membership rule), so markers and prose ids can be resolved and validated.
 */
internal class CatalogIndex(definition: ArchitectureDefinition) {
    val groups: List<RuleGroup> = definition.groups

    init {
        prepare(groups)
    }

    val groupsById: Map<String, RuleGroup> = groups.associateBy { it.id }
    val constructsById: Map<String, Construct<*>> = groups.flatMap { it.constructs }.associateBy { it.id }
    val rulesById: Map<String, Rule> = buildMap {
        groups.forEach { group ->
            group.declaredRules.forEach { put(it.id, it) }
            group.constructs.flatMap { it.declaredRules }.forEach { put(it.id, it) }
            if (group.inPackage != null) exhaustiveRule(group).let { put(it.id, it) }
        }
        definition.membership?.let { universe -> membershipRule(groups, universe).let { put(it.id, it) } }
    }

    /** Every id a doc may legitimately reference in prose. */
    val knownIds: Set<String> = groupsById.keys + constructsById.keys + rulesById.keys
}
