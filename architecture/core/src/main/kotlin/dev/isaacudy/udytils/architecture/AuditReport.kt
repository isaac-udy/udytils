package dev.isaacudy.udytils.architecture

/**
 * One audit finding: the rule it belongs to and the location, message, and supporting evidence
 * lines from the check. A grouped finding (one candidate, several declarations) puts the
 * declarations in [evidence]; the report counts the finding once.
 */
data class AuditFinding(
    val rule: Rule,
    val where: String,
    val message: String,
    val evidence: List<String> = emptyList(),
)

/**
 * Aggregate audit report across all audited rules in an [ArchitectureRun].
 */
class AuditReport(
    val findings: List<AuditFinding>,
) {
    val count: Int get() = findings.size
    val isEmpty: Boolean get() = findings.isEmpty()
}

/** Collects audit findings for every active rule with an audit in the run. */
fun auditReport(run: ArchitectureRun): AuditReport {
    val findings = run.rules
        .filter { it.status is Status.Active }
        .filter { (it.enforcement as? NotEnforced)?.audit != null }
        .flatMap { rule -> run.auditFindings(rule).map { AuditFinding(rule, it.where, it.message, it.evidence) } }
    return AuditReport(findings)
}

/** Renders the audit report as Markdown. */
fun renderAuditReport(report: AuditReport): String {
    if (report.isEmpty) return "No audit findings.\n"
    return buildString {
        appendLine("# Architecture Audit Report")
        appendLine()
        appendLine("${report.count} finding(s) across ${report.findings.map { it.rule.id }.distinct().size} rule(s):")
        report.findings.groupBy { it.rule }.toList().sortedBy { (rule, _) -> rule.id }.forEach { (rule, group) ->
            appendLine()
            appendLine("## `${rule.id}`")
            appendLine()
            appendLine(rule.title)
            appendLine()
            group.forEach { finding ->
                appendLine("- `${finding.where}`: ${finding.message}")
                finding.evidence.forEach { appendLine("    - $it") }
            }
        }
    }
}
