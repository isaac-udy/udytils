package dev.isaacudy.udytils.mutation.gradle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal enum class Status(val label: String) {
    Killed("killed"),
    TimedOut("timed-out"),
    Survived("survived"),
    Uncovered("uncovered"),
}

@Serializable
internal data class MutantResult(
    val id: String,
    val path: String,
    val module: String?,
    val line: Int,
    val operator: String,
    val variant: String,
    val declaration: String,
    val text: String,
    val callee: String? = null,
    val constructs: List<String>,
    val expected: List<String>,
    val rules: List<String>,
    val status: String,
    val killedBy: String? = null,
    val failure: String? = null,
    val testsRun: Int = 0,
    val candidateTests: Int = 0,
    val millis: Long = 0,
    val fingerprint: String = "",
)

@Serializable
internal data class BaselineFailure(val module: String, val test: String)

@Serializable
internal data class Summary(
    val mutants: Int,
    val killed: Int,
    val timedOut: Int,
    val survived: Int,
    val uncovered: Int,
    val notExpected: Int,
    val ignored: Int,
    val notSelected: Int,
    val excludedCalls: Int = 0,
    val notSampled: Int = 0,
    /** The seed that picked the sampled mutants; null when no operator is sampled. */
    val sampleSeed: Long? = null,
    val score: Double?,
    val millis: Long,
)

@Serializable
internal data class Report(
    val version: Int = 1,
    /** When the run started, in ISO-8601 UTC. */
    val startedAt: String? = null,
    val hosts: List<String>,
    val summary: Summary,
    val mutants: List<MutantResult>,
    val baselineFailures: List<BaselineFailure>,
    val sources: SourceDigest? = null,
)

internal val reportJson = Json { prettyPrint = true; encodeDefaults = true }
internal val lineJson = Json { encodeDefaults = true }

internal object TextReport {
    fun render(report: Report): String = buildString {
        val s = report.summary
        appendLine("Mutation run on ${report.hosts.joinToString()}: ${s.mutants} mutants in ${s.millis / 1000} s.")
        appendLine("  killed ${s.killed}, timed out ${s.timedOut}, survived ${s.survived}, uncovered ${s.uncovered}" +
            (s.score?.let { ", score %.1f%%".format(it * 100) } ?: ""))
        appendLine("  not run: ${s.notExpected} expect no tests, ${s.ignored} ignored, ${s.notSelected} selected by no rule" +
            ", ${s.excludedCalls} excluded by excludeCalls, ${s.notSampled} left out by sampling")
        s.sampleSeed?.let { appendLine("  sample seed $it: -P${MutationPlugin.SAMPLE_SEED_PROPERTY}=$it runs the same sample") }
        if (report.baselineFailures.isNotEmpty()) {
            appendLine("Baseline failures (excluded from every mutant run):")
            report.baselineFailures.forEach { appendLine("  ${it.module} ${it.test}") }
        }
        val survivors = report.mutants.filter { it.status == Status.Survived.label }
        if (survivors.isNotEmpty()) {
            appendLine("Survived:")
            survivors.sortedWith(compareBy({ it.path }, { it.line })).forEach {
                appendLine("  ${it.path}:${it.line} ${it.operator} (${it.variant}) `${it.text}` [${it.id}] (${it.candidateTests} tests)")
            }
        }
        val uncovered = report.mutants.filter { it.status == Status.Uncovered.label }
        if (uncovered.isNotEmpty()) {
            appendLine("Uncovered (no test in an expected category executes the mutant):")
            uncovered.sortedWith(compareBy({ it.path }, { it.line })).forEach {
                appendLine("  ${it.path}:${it.line} ${it.operator} (${it.variant}) `${it.text}` [${it.id}] expected ${it.expected.ifEmpty { listOf("any") }.joinToString("/")}")
            }
        }
    }
}

/** One mutant site of the last run's manifests, selected or not; written to `build/mutation/sites.json`. */
@Serializable
internal data class SiteListing(
    val id: String,
    val path: String,
    val line: Int,
    val operator: String,
    val variant: String,
    val text: String,
    val callee: String? = null,
    val constructs: List<String>,
    val rules: List<String>,
)
