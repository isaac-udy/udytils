package dev.isaacudy.udytils.metrics

/**
 * Renders the metrics series as a single self-contained HTML page: one chart per
 * (source, metric) with a line per subject, and the latest run's findings grouped by source.
 * No external resources; inline SVG and CSS only.
 */
fun renderMetricsReport(runs: List<MetricsRun>, title: String = "Codebase health"): String {
    if (runs.isEmpty()) return "<h1>${title.escape()}</h1><p>No metrics runs recorded yet.</p>"
    val latest = runs.last()

    data class SeriesKey(val source: String, val metric: String, val label: String)

    val series = linkedMapOf<SeriesKey, MutableList<Double?>>()
    runs.forEachIndexed { runIndex, run ->
        run.records.forEach { record ->
            val label = (listOf(record.subject) + record.dimensions.entries.sortedBy { it.key }.map { it.value })
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            val key = SeriesKey(record.source, record.metric, label)
            val values = series.getOrPut(key) { MutableList(runs.size) { null } }
            values[runIndex] = record.value
        }
    }

    val charts = series.entries
        .groupBy { it.key.source to it.key.metric }
        .entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))

    return buildString {
        appendLine("<title>${title.escape()}</title>")
        appendLine("<style>$CSS</style>")
        appendLine("<h1>${title.escape()}</h1>")
        appendLine("<p class=\"meta\">${runs.size} run(s) · latest: <code>${latest.commit.take(9)}</code> on <code>${latest.branch.escape()}</code> at ${latest.timestamp.escape()}</p>")

        charts.forEach { (key, lines) ->
            val (source, metric) = key
            appendLine("<section>")
            appendLine("<h2>${source.escape()} — ${metric.escape()}</h2>")
            appendLine(renderChart(lines.associate { it.key.label to it.value }, runs))
            appendLine("</section>")
        }

        val findings = latest.findings.groupBy { it.source }
        if (findings.isNotEmpty()) {
            appendLine("<h1>Latest findings</h1>")
            findings.entries.sortedBy { it.key }.forEach { (source, items) ->
                appendLine("<section>")
                appendLine("<h2>${source.escape()} (${items.size})</h2>")
                appendLine("<table><tr><th>Subject</th><th>Detail</th></tr>")
                items.forEach {
                    val detail = (listOf(it.message) + it.dimensions.entries.map { d -> "${d.key}=${d.value}" })
                        .joinToString(" · ")
                    appendLine("<tr><td>${it.subject.escape()}</td><td>${detail.escape()}</td></tr>")
                }
                appendLine("</table>")
                appendLine("</section>")
            }
        }
    }
}

private const val WIDTH = 860
private const val HEIGHT = 220
private const val PAD = 8
private const val MAX_LINES = 12
private val PALETTE = listOf(
    "#2563eb", "#dc2626", "#059669", "#d97706", "#7c3aed", "#0891b2",
    "#be185d", "#4d7c0f", "#b45309", "#1e40af", "#9f1239", "#115e59",
)

private fun renderChart(lines: Map<String, List<Double?>>, runs: List<MetricsRun>): String {
    val shown = lines.entries
        .sortedByDescending { it.value.lastOrNull { v -> v != null } ?: 0.0 }
        .take(MAX_LINES)
    val dropped = lines.size - shown.size
    val max = shown.flatMap { it.value.filterNotNull() }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val stepX = if (runs.size > 1) (WIDTH - 2.0 * PAD) / (runs.size - 1) else 0.0

    fun x(i: Int) = PAD + i * stepX
    fun y(v: Double) = HEIGHT - PAD - (v / max) * (HEIGHT - 2.0 * PAD)

    return buildString {
        appendLine("<div class=\"chart\">")
        appendLine("<svg viewBox=\"0 0 $WIDTH $HEIGHT\" preserveAspectRatio=\"none\">")
        appendLine("<line x1=\"$PAD\" y1=\"${HEIGHT - PAD}\" x2=\"${WIDTH - PAD}\" y2=\"${HEIGHT - PAD}\" class=\"axis\"/>")
        shown.forEachIndexed { index, (_, values) ->
            val color = PALETTE[index % PALETTE.size]
            val points = values.mapIndexedNotNull { i, v -> v?.let { "${x(i)},${y(it)}" } }
            if (points.size == 1) {
                val (px, py) = points.single().split(',')
                appendLine("<circle cx=\"$px\" cy=\"$py\" r=\"3\" fill=\"$color\"/>")
            } else {
                appendLine("<polyline points=\"${points.joinToString(" ")}\" fill=\"none\" stroke=\"$color\" stroke-width=\"2\"/>")
            }
        }
        appendLine("</svg>")
        appendLine("<div class=\"legend\">")
        appendLine("<span class=\"scale\">max ${max.pretty()}</span>")
        shown.forEachIndexed { index, (label, values) ->
            val color = PALETTE[index % PALETTE.size]
            val last = values.lastOrNull { it != null }?.pretty() ?: "—"
            appendLine("<span><i style=\"background:$color\"></i>${label.escape()} <b>$last</b></span>")
        }
        if (dropped > 0) appendLine("<span class=\"more\">+$dropped more series</span>")
        appendLine("</div>")
        appendLine("</div>")
    }
}

private fun Double.pretty(): String = if (this == toLong().toDouble()) toLong().toString() else "%.2f".format(this)

private fun String.escape(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private val CSS = """
    body { font: 14px/1.5 -apple-system, 'Segoe UI', sans-serif; margin: 2rem auto; max-width: 920px; color: #1f2937; padding: 0 1rem; }
    h1 { font-size: 1.4rem; border-bottom: 1px solid #e5e7eb; padding-bottom: .4rem; margin-top: 2.5rem; }
    h2 { font-size: 1rem; margin: 1.2rem 0 .4rem; }
    .meta { color: #6b7280; }
    .chart svg { width: 100%; height: 220px; background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 6px; }
    .axis { stroke: #d1d5db; stroke-width: 1; }
    .legend { display: flex; flex-wrap: wrap; gap: .3rem 1rem; margin-top: .4rem; font-size: 12px; color: #4b5563; }
    .legend i { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 4px; }
    .legend .scale, .legend .more { color: #9ca3af; }
    table { border-collapse: collapse; width: 100%; font-size: 13px; }
    th, td { text-align: left; border-bottom: 1px solid #e5e7eb; padding: .3rem .5rem; vertical-align: top; }
    th { color: #6b7280; font-weight: 600; }
    code { background: #f3f4f6; padding: .1rem .3rem; border-radius: 3px; }
""".trimIndent()
