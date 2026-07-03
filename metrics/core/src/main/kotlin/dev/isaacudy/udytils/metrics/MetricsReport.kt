package dev.isaacudy.udytils.metrics

/**
 * Renders the metrics series as a set of self-contained HTML pages (file name → content):
 *
 *  - `index.html` — a module × metric table of the latest values; each metric column links to
 *    its page.
 *  - `metric-<source>-<metric>.html` — one page per metric: a trend chart of the codebase total
 *    (all subjects summed, not split by module), the latest per-subject values, and the source's
 *    found items from the latest run.
 *
 * No external resources; inline SVG and CSS only.
 */
fun renderMetricsReportPages(runs: List<MetricsRun>, title: String = "Codebase health"): Map<String, String> {
    if (runs.isEmpty()) {
        return mapOf("index.html" to page(title, "<h1>${title.escape()}</h1><p>No metrics runs recorded yet.</p>"))
    }
    val latest = runs.last()
    val metrics = runs.flatMap { run -> run.records.map { it.source to it.metric } }.distinct()
        .sortedWith(compareBy({ it.first }, { it.second }))

    val pages = linkedMapOf<String, String>()
    pages["index.html"] = page(title, renderHome(title, runs, latest, metrics))
    metrics.forEach { (source, metric) ->
        pages[fileFor(source, metric)] = page(
            "$source — $metric",
            renderMetricPage(source, metric, runs, latest),
        )
    }
    return pages
}

private fun fileFor(source: String, metric: String): String =
    "metric-$source-${metric.replace(Regex("[^A-Za-z0-9]+"), "-")}.html"

private fun renderHome(
    title: String,
    runs: List<MetricsRun>,
    latest: MetricsRun,
    metrics: List<Pair<String, String>>,
): String = buildString {
    appendLine("<h1>${title.escape()}</h1>")
    appendLine(meta(runs, latest))

    // Module rows form a tree (":app:client:desktop" → :app › :client › :desktop). Every prefix
    // is a row; group rows sum every record under their subtree. The `project` subject is its own
    // row, and ":" (the root project) is a leaf: nothing starts with "::".
    val moduleSubjects = latest.records.map { it.subject }.distinct().filter { it != "project" }
    val paths = moduleSubjects.flatMap { subject ->
        val segments = subject.trimStart(':').split(':').filter { it.isNotEmpty() }
        if (segments.isEmpty()) listOf(":")
        else segments.runningReduce { acc, part -> "$acc:$part" }.map { ":$it" }
    }.distinct().sorted()

    fun cell(source: String, metric: String, match: (String) -> Boolean): Double? =
        latest.records
            .filter { it.source == source && it.metric == metric && match(it.subject) }
            .takeIf { it.isNotEmpty() }
            ?.sumOf { it.value }

    fun row(label: String, path: String?, depth: Int, hasChildren: Boolean, match: (String) -> Boolean): String {
        val indent = "&nbsp;&nbsp;".repeat(depth)
        val toggle = if (hasChildren) "<span class=\"toggle\">▾</span> " else "<span class=\"toggle\"></span> "
        val attrs = if (path != null) " data-path=\"${path.escape()}\" data-group=\"$hasChildren\"" else ""
        val cells = metrics.joinToString("") { (source, metric) ->
            "<td class=\"num\">${cell(source, metric, match)?.pretty() ?: ""}</td>"
        }
        return "<tr$attrs><td class=\"subject\">$indent$toggle${label.escape()}</td>$cells</tr>"
    }

    appendLine("<table id=\"modules\">")
    appendLine("<tr><th>module</th>${metrics.joinToString("") { (source, metric) ->
        "<th><a href=\"${fileFor(source, metric)}\" title=\"$source — $metric\">${metric.split('.').joinToString("<br>") { it.escape() }}</a></th>"
    }}</tr>")
    appendLine(row("project", null, 0, false) { it == "project" })
    paths.forEach { path ->
        val hasChildren = paths.any { it != path && it.startsWith("$path:") }
        val label = ":" + path.substringAfterLast(':')
        val depth = (path.count { it == ':' } - 1).coerceAtLeast(0)
        appendLine(row(label, path, depth, hasChildren) { it == path || it.startsWith("$path:") })
    }
    appendLine("</table>")
    appendLine(COLLAPSE_SCRIPT)
}

private val COLLAPSE_SCRIPT = """
    <script>
    (function () {
        var rows = Array.from(document.querySelectorAll('#modules tr[data-path]'));
        var collapsed = new Set();
        function children(path) {
            return rows.filter(function (r) { return r.dataset.path.startsWith(path + ':') &&
                r.dataset.path.slice(path.length + 1).indexOf(':') === -1; });
        }
        function hideSubtree(path) {
            rows.forEach(function (r) {
                if (r.dataset.path.startsWith(path + ':')) r.style.display = 'none';
            });
        }
        function showChildren(path) {
            children(path).forEach(function (r) {
                r.style.display = '';
                if (r.dataset.group === 'true' && !collapsed.has(r.dataset.path)) showChildren(r.dataset.path);
            });
        }
        rows.forEach(function (r) {
            if (r.dataset.group !== 'true') return;
            r.querySelector('.toggle').style.cursor = 'pointer';
            r.querySelector('.toggle').addEventListener('click', function () {
                var path = r.dataset.path;
                if (collapsed.has(path)) {
                    collapsed.delete(path);
                    this.textContent = '▾';
                    showChildren(path);
                } else {
                    collapsed.add(path);
                    this.textContent = '▸';
                    hideSubtree(path);
                }
            });
        });
    })();
    </script>
""".trimIndent()

private fun renderMetricPage(source: String, metric: String, runs: List<MetricsRun>, latest: MetricsRun): String = buildString {
    appendLine("<p class=\"crumb\"><a href=\"index.html\">← all metrics</a></p>")
    appendLine("<h1>${source.escape()} — ${metric.escape()}</h1>")
    appendLine(meta(runs, latest))

    // Codebase total per run: every subject and dimension summed.
    val totals = runs.map { run ->
        run.records.filter { it.source == source && it.metric == metric }
            .takeIf { it.isNotEmpty() }
            ?.sumOf { it.value }
    }
    appendLine("<h2>Codebase total</h2>")
    appendLine(renderChart(totals))

    val latestRecords = latest.records
        .filter { it.source == source && it.metric == metric }
        .sortedWith(compareByDescending { it.value })
    appendLine("<h2>Latest values</h2>")
    appendLine("<table><tr><th>module</th><th>detail</th><th class=\"num\">value</th></tr>")
    latestRecords.forEach { record ->
        val detail = record.dimensions.entries.sortedBy { it.key }.joinToString(" · ") { "${it.key}=${it.value}" }
        appendLine("<tr><td class=\"subject\">${record.subject.escape()}</td><td>${detail.escape()}</td><td class=\"num\">${record.value.pretty()}</td></tr>")
    }
    appendLine("</table>")

    val findings = latest.findings.filter { it.source == source }
    if (findings.isNotEmpty()) {
        appendLine("<h2>Found items (${findings.size})</h2>")
        appendLine("<table><tr><th>module</th><th>item</th></tr>")
        findings.forEach { finding ->
            val detail = (listOf(finding.message) + finding.dimensions.entries.sortedBy { it.key }.map { "${it.key}=${it.value}" })
                .joinToString(" · ")
            appendLine("<tr><td class=\"subject\">${finding.subject.escape()}</td><td>${detail.escape()}</td></tr>")
        }
        appendLine("</table>")
    }
}

private fun meta(runs: List<MetricsRun>, latest: MetricsRun): String =
    "<p class=\"meta\">${runs.size} run(s) · latest: <code>${latest.commit.take(9)}</code> on " +
        "<code>${latest.branch.escape()}</code> at ${latest.timestamp.escape()}</p>"

private const val WIDTH = 860
private const val HEIGHT = 200
private const val PAD = 8

/** A single-series line chart of one value per run; `null` = the metric wasn't recorded that run. */
private fun renderChart(values: List<Double?>): String {
    val max = values.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val stepX = if (values.size > 1) (WIDTH - 2.0 * PAD) / (values.size - 1) else 0.0
    fun x(i: Int) = PAD + i * stepX
    fun y(v: Double) = HEIGHT - PAD - (v / max) * (HEIGHT - 2.0 * PAD)
    val points = values.mapIndexedNotNull { i, v -> v?.let { "${x(i)},${y(it)}" } }
    val latest = values.lastOrNull { it != null }

    return buildString {
        appendLine("<div class=\"chart\">")
        appendLine("<svg viewBox=\"0 0 $WIDTH $HEIGHT\" preserveAspectRatio=\"none\">")
        appendLine("<line x1=\"$PAD\" y1=\"${HEIGHT - PAD}\" x2=\"${WIDTH - PAD}\" y2=\"${HEIGHT - PAD}\" class=\"axis\"/>")
        if (points.size == 1) {
            val (px, py) = points.single().split(',')
            appendLine("<circle cx=\"$px\" cy=\"$py\" r=\"3\" fill=\"#2563eb\"/>")
        } else if (points.isNotEmpty()) {
            appendLine("<polyline points=\"${points.joinToString(" ")}\" fill=\"none\" stroke=\"#2563eb\" stroke-width=\"2\"/>")
        }
        appendLine("</svg>")
        appendLine("<div class=\"legend\"><span class=\"scale\">max ${max.pretty()}</span><span>latest <b>${latest?.pretty() ?: "—"}</b></span></div>")
        appendLine("</div>")
    }
}

private fun page(title: String, body: String): String =
    "<title>${title.escape()}</title>\n<style>$CSS</style>\n$body"

private fun Double.pretty(): String = if (this == toLong().toDouble()) toLong().toString() else "%.2f".format(this)

private fun String.escape(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private val CSS = """
    body { font: 13px/1.5 ui-monospace, 'SF Mono', Menlo, Consolas, monospace; margin: 2rem auto; max-width: 980px; color: #1f2937; padding: 0 1rem; }
    h1 { font-size: 1.2rem; border-bottom: 1px solid #e5e7eb; padding-bottom: .4rem; margin-top: 1.5rem; }
    h2 { font-size: 1rem; margin: 1.6rem 0 .5rem; }
    .meta, .crumb { color: #6b7280; }
    a { color: #2563eb; text-decoration: none; }
    a:hover { text-decoration: underline; }
    .chart svg { width: 100%; height: 200px; background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 6px; }
    .axis { stroke: #d1d5db; stroke-width: 1; }
    .legend { display: flex; gap: 1rem; margin-top: .4rem; font-size: 12px; color: #4b5563; }
    .legend .scale { color: #9ca3af; }
    table { border-collapse: collapse; width: 100%; font-size: 12px; overflow-x: auto; display: block; }
    th, td { text-align: left; border-bottom: 1px solid #e5e7eb; padding: .25rem .45rem; vertical-align: top; white-space: nowrap; }
    td:last-child, th:last-child { white-space: normal; }
    th { color: #6b7280; font-weight: 600; font-size: 10px; vertical-align: bottom; }
    th a { color: #6b7280; }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .subject { color: #374151; }
    .toggle { display: inline-block; width: 1em; color: #9ca3af; user-select: none; }
    code { background: #f3f4f6; padding: .1rem .3rem; border-radius: 3px; }
""".trimIndent()
