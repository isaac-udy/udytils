package dev.isaacudy.udytils.architecture.docs

import dev.isaacudy.udytils.architecture.ArchitectureDefinition
import dev.isaacudy.udytils.architecture.Status
import dev.isaacudy.udytils.architecture.Tag
import dev.isaacudy.udytils.architecture.exhaustiveRule
import dev.isaacudy.udytils.architecture.membershipRule
import dev.isaacudy.udytils.architecture.prepare

/**
 * The `docs/rule-index.md` page: every construct and rule in the catalog, in document order — per
 * group, each construct (a `construct` classification row whose statement is its AND-composed
 * requirements) with its rules, then the group-level rules, then the layer's exhaustiveness rule;
 * finally the global membership rule.
 */
internal fun renderRuleIndexDoc(definition: ArchitectureDefinition, sourceLinkBase: String): String = buildString {
    appendLine("# Rule index")
    appendLine()
    appendLine(
        "The complete catalog, one row per Construct or Rule. IDs are based on the " +
            "object/property that declares the entry (see the [README](../README.md)). " +
            "Enforcement markers link to the declaring source and are explained below the table.",
    )
    appendLine()
    appendLine(renderRuleIndexTable(definition, sourceLinkBase))
    appendLine()
    appendLine(ENFORCEMENT_LEGEND)
}

private val ENFORCEMENT_LEGEND = """
    ## Enforcement status

    Each status is derived from how the entry is declared:

    | Status | Meaning | Declared as |
    | --- | --- | --- |
    | `tested` | A test enforces the Rule and fails citing its ID. | a `rule` ending in `scope { }` / `constrain { }` / `moduleGraph { }` / `enforcedBy(...)` |
    | `construct` | A classification. A declaration matching no Construct (or more than one) fails the RuleGroup's exhaustiveness test. | a `Construct(...)`'s requirements |
    | `unverifiable` | A mandatory Rule that tests can't reliably verify; enforced by review. | a `rule` ending in `unverifiable()` |
    | `guidance` | An advisory statement; enforced by review. May declare an `audit { }`: a test that reports non-conforming code without ever failing. | `@Describe("…") val x by guidance` |
    | `codegen` | Guaranteed by a code generator; there is nothing in source to test. | a `rule` ending in `codegen()` |
""".trimIndent()

internal fun renderRuleIndexTable(definition: ArchitectureDefinition, sourceLinkBase: String): String {
    val groups = definition.groups
    prepare(groups)
    data class Row(val id: String, val statement: String, val marker: String, val source: String)

    fun sourceOf(owner: Any): String =
        "$sourceLinkBase/${owner.javaClass.packageName.replace('.', '/')}/${owner.javaClass.simpleName}.kt"

    val rows = mutableListOf<Row>()
    groups.forEach { group ->
        group.constructs.forEach { construct ->
            rows += Row(construct.id, construct.requirements.joinToString(" · ") { it.description }, Tag.CONSTRUCT.marker, sourceOf(construct))
            construct.declaredRules.filter { it.status is Status.Active }.forEach {
                rows += Row(it.id, it.title, it.tag.marker, sourceOf(construct))
            }
        }
        group.declaredRules.filter { it.status is Status.Active }.forEach {
            rows += Row(it.id, it.title, it.tag.marker, sourceOf(group))
        }
        if (group.inPackage != null) exhaustiveRule(group).let { rows += Row(it.id, it.title, it.tag.marker, sourceOf(group)) }
    }
    definition.membership?.let { universe ->
        membershipRule(groups, universe).let { rows += Row(it.id, it.title, it.tag.marker, sourceOf(definition)) }
    }
    return buildString {
        appendLine("| Rule | Statement | Enforcement |")
        appendLine("| --- | --- | --- |")
        rows.forEach { (id, statement, marker, source) ->
            appendLine("| `$id` | ${statement.replace("|", "\\|")} | [$marker]($source) |")
        }
    }.trimEnd()
}
