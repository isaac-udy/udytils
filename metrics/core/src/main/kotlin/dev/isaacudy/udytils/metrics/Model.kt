package dev.isaacudy.udytils.metrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One numeric measurement. Metrics are the graphable layer of a run: the report draws each
 * distinct (source, metric, subject, dimensions) combination as a series over time.
 *
 * @param source the integration that produced the record (`architecture`, `loc`, `readme`, …)
 * @param subject what was measured — usually a module path, `project` for project-wide values
 * @param metric the measurement name, dot-namespaced (`loc.kotlin`, `exceptions.count`)
 * @param value the measurement; always numeric so any metric can be charted
 * @param dimensions extra identity for the series (rule ID, warning category, …)
 */
@Serializable
data class MetricRecord(
    val source: String,
    val subject: String,
    val metric: String,
    val value: Double,
    val dimensions: Map<String, String> = emptyMap(),
)

/**
 * One itemized detail attached to a run: a build warning, a stale README, an architecture
 * exception. Findings power the drill-down view of the latest run; counts of them usually also
 * appear as [MetricRecord]s so they can be tracked over time.
 */
@Serializable
data class MetricFinding(
    val source: String,
    val subject: String,
    val message: String,
    val dimensions: Map<String, String> = emptyMap(),
)

/** What an integration task emits; [CollectMetrics] merges these into a [MetricsRun]. */
@Serializable
data class IntegrationOutput(
    val records: List<MetricRecord> = emptyList(),
    val findings: List<MetricFinding> = emptyList(),
)

/** One collection run: all records and findings for one commit at one point in time. */
@Serializable
data class MetricsRun(
    val commit: String,
    val branch: String,
    val timestamp: String,
    val records: List<MetricRecord> = emptyList(),
    val findings: List<MetricFinding> = emptyList(),
)

val metricsJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = false
}
