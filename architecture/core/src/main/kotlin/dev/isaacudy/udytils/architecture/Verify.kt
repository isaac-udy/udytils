package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import kotlin.test.fail

/**
 * One architecture run over a catalog: builds the Konsist scope + module graph **once** and evaluates
 * rules on demand. Lets the aggregate [verify] and the per-rule `@TestFactory` share a single scan.
 */
class ArchitectureRun(
    groups: List<RuleGroup>,
    scopeProvider: () -> KoScope,
    membership: ((KoBaseDeclaration) -> Boolean)? = null,
    testScopeProvider: (() -> KoScope)? = null,
    testMembership: ((KoBaseDeclaration) -> Boolean)? = null,
) {
    constructor(definition: ArchitectureDefinition) : this(
        definition.groups,
        definition.scope,
        definition.membership,
        definition.testScope,
        definition.testMembership,
    )

    /** Every enforced rule, in document order (see [enforcedRules]). */
    val rules: List<Rule> = enforcedRules(groups, membership, testMembership)

    /** The production and test scopes, each built at most once. */
    val scopes: ArchitectureScopes = ArchitectureScopes(
        scopeProvider,
        testScopeProvider,
        groups.filter { it.codeScope == CodeScope.Production }.flatMap { it.constructs },
    )
    private val graph: ModuleGraph by lazy { ModuleGraph.parse() }

    init {
        integrityChecks(rules)
        val testGroups = groups.filter { it.codeScope == CodeScope.Test }.map { it.id }
        if (testGroups.isNotEmpty() && testScopeProvider == null) {
            fail("Test-scoped RuleGroups $testGroups need a testScope on the ArchitectureDefinition")
        }
    }

    /** Violations for one rule (empty = passes). Delegated / guidance / codegen rules run nothing. */
    fun violations(rule: Rule): List<Violation> = run(rule, rule.enforcement)

    /** Findings for a guidance audit — reported, never failing. Empty for rules without an audit. */
    fun auditFindings(rule: Rule): List<Violation> =
        (rule.enforcement as? NotEnforced)?.audit?.let { run(rule, it) }.orEmpty()

    private fun run(rule: Rule, enforcement: Enforcement): List<Violation> = when (enforcement) {
        is ScopeConstraint -> enforcement.check.run(scopes.of(rule.codeScope)) { decl -> Exemptions.isExempt(rule.id, decl) }
        is ScopesConstraint -> enforcement.check.run(scopes) { decl -> Exemptions.isExempt(rule.id, decl) }
        is ModuleGraphConstraint -> enforcement.check.run(graph) { edge -> Exemptions.isExempt(rule.id, edge) }
        else -> emptyList()
    }
}

/**
 * The single aggregate entry point: runs every active rule and fails once with a grouped report.
 * [exclude] drops rules by id (e.g. a downstream overlay).
 */
fun verify(definition: ArchitectureDefinition, exclude: Set<String> = emptySet()) =
    verify(ArchitectureRun(definition), exclude)

/** [verify] over an explicit run — also the engine-test entry point (sentinel catalogs). */
fun verify(run: ArchitectureRun, exclude: Set<String> = emptySet()) {
    val findings = run.rules
        .filter { it.status is Status.Active && it.id !in exclude }
        .flatMap { rule -> run.violations(rule).map { Finding(rule, it.where, it.message, it.evidence) } }
    if (findings.isNotEmpty()) fail(render(findings))
}

/**
 * Every rule the catalog enforces, in document order: per group the group-level rules, then each
 * construct's rules, then the layer's exhaustiveness rule; finally the cross-layer membership rules
 * for the universes the definition provides. The single source for both [verify]/[ArchitectureRun]
 * and the rule index.
 */
internal fun enforcedRules(
    groups: List<RuleGroup>,
    membership: ((KoBaseDeclaration) -> Boolean)?,
    testMembership: ((KoBaseDeclaration) -> Boolean)? = null,
): List<Rule> {
    prepare(groups)
    val rules = mutableListOf<Rule>()
    groups.forEach { group ->
        rules += group.declaredRules
        group.constructs.forEach { rules += it.declaredRules }
        if (group.inPackage != null) rules += exhaustiveRule(group)
    }
    if (membership != null) rules += membershipRule(groups, membership)
    if (testMembership != null) rules += testMembershipRule(groups, testMembership)
    return rules
}

private data class Finding(val rule: Rule, val where: String, val message: String, val evidence: List<String>)

private fun integrityChecks(rules: List<Rule>) {
    val duplicates = rules.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isNotEmpty()) fail("Duplicate rule ids in the catalog: ${duplicates.sorted()}")
    val known = rules.map { it.id }.toSet()
    val dangling = rules.mapNotNull { rule ->
        (rule.enforcement as? DelegatedConstraint)?.by
            ?.filterNot { it in known }?.takeIf { it.isNotEmpty() }?.let { rule.id to it }
    }
    if (dangling.isNotEmpty()) fail("enforcedBy(...) references unknown rule ids: $dangling")
}

private fun render(findings: List<Finding>): String = buildString {
    appendLine("Architecture verification failed — ${findings.size} violation(s):")
    findings.groupBy { it.rule }.toList().sortedBy { (rule, _) -> rule.id }.forEach { (rule, group) ->
        appendLine()
        appendLine("[${rule.id}] ${rule.title}")
        if (rule.rationale.isNotBlank()) appendLine(rule.rationale.trim().prependIndent("    "))
        group.forEach { finding ->
            appendLine("  - ${finding.where}: ${finding.message}")
            finding.evidence.forEach { appendLine("      - $it") }
        }
    }
}
