package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditReportTest {

    // -- fixtures: a catalog with audited and non-audited rules --

    @Describe("Group with audited rules")
    private object AuditGroup : RuleGroup() {
        @Describe("An unverifiable rule with an audit that finds violations")
        val auditedRule by rule {
            unverifiable(ScopeCheck { _, _ ->
                listOf(
                    Violation("com/example/Foo.kt:10", "field `bar` should be private"),
                    Violation("com/example/Baz.kt:25", "field `qux` should be private"),
                )
            })
        }

        @Describe("A guidance item with an audit")
        val guidedRule by guidance {
            audit(ScopeCheck { _, _ ->
                listOf(Violation("com/example/Other.kt:5", "naming does not follow convention"))
            })
        }

        @Describe("An enforced rule (no audit)")
        val enforcedRule by rule {
            scope { _, _ -> emptyList() }
        }

        @Describe("An unverifiable rule without an audit")
        val plainUnverifiable by rule {
            unverifiable()
        }
    }

    // Not private: a Construct resolves its owning group through `objectInstance`, which needs
    // reflective access to the group object.
    @Describe("A construct whose guidance audits over the whole scope")
    object Widget : Construct<ScopedAuditGroup>(requirements = listOf(isClass)) {
        @Describe("Widgets should be grouped")
        val grouped by guidance {
            auditScope { _, _ ->
                listOf(Violation("WidgetConsumer", "three widgets with one consumer", listOf("A", "B", "C")))
            }
        }
    }

    @Describe("Group with a scope-audited construct")
    object ScopedAuditGroup : RuleGroup(constructs = listOf(Widget))

    private val emptyDir = File(System.getProperty("user.dir"), "build/tmp/audit-test-scope")
        .also { it.mkdirs() }

    @Test
    fun `construct guidance can audit over the whole scope and carry evidence`() {
        val scopedRun = ArchitectureRun(
            listOf(ScopedAuditGroup),
            scopeProvider = { Konsist.scopeFromExternalDirectory(emptyDir.absolutePath) },
        )
        val report = auditReport(scopedRun)
        assertEquals(1, report.count)
        val finding = report.findings.single()
        assertEquals("ScopedAuditGroup.Widget.grouped", finding.rule.id)
        assertEquals("WidgetConsumer", finding.where)
        assertEquals(listOf("A", "B", "C"), finding.evidence)
    }

    private fun run() = ArchitectureRun(
        listOf(AuditGroup),
        scopeProvider = { Konsist.scopeFromExternalDirectory(emptyDir.absolutePath) },
    )

    @Test
    fun `audit report collects findings from audited rules only`() {
        val report = auditReport(run())
        assertEquals(3, report.count)
        val ruleIds = report.findings.map { it.rule.id }.distinct().sorted()
        assertEquals(listOf("AuditGroup.auditedRule", "AuditGroup.guidedRule"), ruleIds)
    }

    @Test
    fun `audit report findings include source location and message`() {
        val report = auditReport(run())
        val first = report.findings.first { it.where == "com/example/Foo.kt:10" }
        assertEquals("field `bar` should be private", first.message)
        assertEquals("AuditGroup.auditedRule", first.rule.id)
    }

    @Test
    fun `audit report is empty when no rules have audits`() {
        @Describe("Group without audits")
        class CleanGroup : RuleGroup()

        val cleanRun = ArchitectureRun(
            listOf(CleanGroup()),
            scopeProvider = { Konsist.scopeFromExternalDirectory(emptyDir.absolutePath) },
        )
        val report = auditReport(cleanRun)
        assertTrue(report.isEmpty)
        assertEquals(0, report.count)
    }

    // -- rendering tests (no ArchitectureRun needed) --

    private fun sampleReport(): AuditReport {
        val rule1 = Rule(
            "GroupA.constructA.ruleAlpha",
            "Alpha rule statement",
            "",
            NotEnforced(Tag.UNVERIFIABLE, ScopeConstraint { _, _ -> emptyList() }),
            Status.Active,
            emptyList(),
        )
        val rule2 = Rule(
            "GroupA.constructB.ruleBeta",
            "Beta guidance statement",
            "",
            NotEnforced(Tag.GUIDANCE, ScopeConstraint { _, _ -> emptyList() }),
            Status.Active,
            emptyList(),
        )
        return AuditReport(
            listOf(
                AuditFinding(rule1, "com/example/Foo.kt:10", "field `bar` should be private"),
                AuditFinding(rule1, "com/example/Baz.kt:25", "field `qux` should be private"),
                AuditFinding(
                    rule2,
                    "com/example/Other.kt:5",
                    "naming does not follow convention",
                    evidence = listOf("declared in `Other`", "referenced from `Another`"),
                ),
            )
        )
    }

    @Test
    fun `rendered markdown lists evidence under its finding and counts the finding once`() {
        val report = sampleReport()
        assertEquals(3, report.count)

        val rendered = renderAuditReport(report)
        val expected = """
            - `com/example/Other.kt:5`: naming does not follow convention
                - declared in `Other`
                - referenced from `Another`
        """.trimIndent()
        assertTrue(rendered.contains(expected), rendered)
        assertTrue(rendered.contains("3 finding(s) across 2 rule(s)"))
    }

    @Test
    fun `rendered markdown groups findings by rule with ids and titles`() {
        val rendered = renderAuditReport(sampleReport())
        assertTrue(rendered.contains("# Architecture Audit Report"))
        assertTrue(rendered.contains("## `GroupA.constructA.ruleAlpha`"))
        assertTrue(rendered.contains("Alpha rule statement"))
        assertTrue(rendered.contains("## `GroupA.constructB.ruleBeta`"))
        assertTrue(rendered.contains("Beta guidance statement"))
        assertTrue(rendered.contains("`com/example/Foo.kt:10`"))
        assertTrue(rendered.contains("field `bar` should be private"))
        assertTrue(rendered.contains("`com/example/Baz.kt:25`"))
        assertTrue(rendered.contains("`com/example/Other.kt:5`"))
    }

    @Test
    fun `rendered markdown for empty report shows clean line`() {
        val report = AuditReport(emptyList())
        assertEquals("No audit findings.\n", renderAuditReport(report))
    }

    @Test
    fun `rendered report orders rules by id`() {
        val rendered = renderAuditReport(sampleReport())
        val alphaPos = rendered.indexOf("GroupA.constructA.ruleAlpha")
        val betaPos = rendered.indexOf("GroupA.constructB.ruleBeta")
        assertTrue(alphaPos < betaPos, "Rules should be ordered by id")
    }
}
