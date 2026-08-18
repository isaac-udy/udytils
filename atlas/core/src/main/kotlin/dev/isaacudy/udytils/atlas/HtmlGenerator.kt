package dev.isaacudy.udytils.atlas

import java.io.File

fun generateHtml(config: ResolvedAtlasConfig, manifest: AtlasManifest): String {
    val json = kotlinx.serialization.json.Json { prettyPrint = true }
    val manifestJson = json.encodeToString(AtlasManifest.serializer(), manifest)

    fun htmlId(qualifiedName: String) = qualifiedName.replace('.', '_')

    val qnToDisplay = manifest.nodes.associate { it.qualifiedName to it.displayName }
    val qnToDefaultImage = manifest.nodes.associate { node ->
        val img = node.variants.getOrElse(node.defaultVariantIndex) { node.variants.firstOrNull() }
        node.qualifiedName to img?.imagePath
    }

    data class EdgeRef(val dest: String, val display: String, val kind: EdgeKind)

    val incomingByDest = mutableMapOf<String, MutableList<EdgeRef>>()
    val outgoingByDest = mutableMapOf<String, MutableList<EdgeRef>>()
    for (edge in manifest.edges) {
        if (edge.source == edge.target) continue
        val srcDisplay = qnToDisplay[edge.source] ?: edge.source
        val tgtDisplay = qnToDisplay[edge.target] ?: edge.target
        incomingByDest.getOrPut(edge.target) { mutableListOf() }
            .add(EdgeRef(edge.source, srcDisplay, edge.kind))
        outgoingByDest.getOrPut(edge.source) { mutableListOf() }
            .add(EdgeRef(edge.target, tgtDisplay, edge.kind))
    }

    fun dedup(refs: List<EdgeRef>): List<EdgeRef> {
        val byDest = refs.groupBy { it.dest }
        return byDest.map { (_, group) ->
            val best = group.minByOrNull { when (it.kind) { EdgeKind.RESULT -> 0; EdgeKind.OPEN -> 1; EdgeKind.CHROME -> 2 } }!!
            best
        }.sortedBy { it.display }
    }

    fun refThumb(qn: String): String {
        val imgPath = qnToDefaultImage[qn]
        return if (imgPath != null) {
            """<img class="ref-thumb" src="images/${esc(imgPath)}" loading="lazy" />"""
        } else {
            """<span class="ref-thumb-placeholder"></span>"""
        }
    }

    val realNodes = manifest.nodes.filter { !it.synthetic }
    val syntheticNodes = manifest.nodes.filter { it.synthetic }
    val nodesByModule = realNodes.groupBy { it.moduleLabel }.toSortedMap()

    return buildString {
        appendLine("""<title>${esc(manifest.projectName)} UI Atlas</title>""")
        appendLine("<style>")
        appendLine(generateCss())
        appendLine("</style>")

        appendLine("""<div class="atlas-header">""")
        appendLine("""  <div class="atlas-title">${esc(manifest.projectName)} UI Atlas</div>""")
        appendLine("""  <div class="atlas-stats">""")
        appendLine("""    <span class="stat">${manifest.nodes.size} screens</span>""")
        appendLine("""    <span class="stat">${manifest.nodes.sumOf { it.variants.size }} variants</span>""")
        appendLine("""    <span class="stat">${manifest.edges.size} edges</span>""")
        if (manifest.unresolvedEdges.isNotEmpty()) {
            appendLine("""    <a class="stat stat-warn" id="unresolved-link" href="#">${manifest.unresolvedEdges.size} unresolved</a>""")
        }
        appendLine("""  </div>""")
        appendLine("""  <div class="atlas-controls">""")
        appendLine("""    <input type="text" id="search-input" placeholder="Search screens..." class="search-box" />""")
        appendLine("""    <button id="zoom-fit-btn" class="ctrl-btn" title="Zoom to fit all">Fit</button>""")
        appendLine("""    <button id="zoom-single-btn" class="ctrl-btn" title="Single-screen zoom level">1:1</button>""")
        appendLine("""    <button id="zoom-in-btn" class="ctrl-btn" title="Zoom in">+</button>""")
        appendLine("""    <button id="zoom-out-btn" class="ctrl-btn" title="Zoom out">&minus;</button>""")
        appendLine("""    <button id="export-csv-btn" class="ctrl-btn" title="Export annotations as CSV">Export CSV</button>""")
        appendLine("""    <button id="notes-btn" class="ctrl-btn" title="Browse annotations">&#9998; <span id="notes-count">0</span></button>""")
        appendLine("""  </div>""")
        appendLine("""  <div class="atlas-timestamp">${esc(manifest.generatedAt)}</div>""")
        appendLine("""</div>""")

        appendLine("""<div id="search-results" class="search-results" hidden></div>""")
        appendLine("""<div id="viewport" class="viewport">""")
        appendLine("""  <div id="world" class="world">""")

        for ((moduleLabel, moduleNodes) in nodesByModule) {
            val featureGroups = moduleNodes.groupBy { it.featureGroup }.toSortedMap()
            val moduleKey = moduleLabel.replace(":", "-")
            appendLine("""    <div class="module-group">""")
            appendLine("""      <div class="module-header" data-collapse-key="mod-$moduleKey">""")
            appendLine("""        <span class="chevron">&#9662;</span>""")
            appendLine("""        <span class="module-name">${esc(moduleLabel)}</span>""")
            appendLine("""        <span class="group-count">(${moduleNodes.size})</span>""")
            appendLine("""      </div>""")
            appendLine("""      <div class="module-body collapsible-body">""")

            for ((feature, featureNodes) in featureGroups) {
                val featureKey = "$moduleKey--$feature"
                appendLine("""        <div class="feature-group">""")
                appendLine("""          <div class="feature-header" data-collapse-key="feat-$featureKey">""")
                appendLine("""            <span class="chevron">&#9662;</span>""")
                appendLine("""            <span class="feature-name">${esc(feature)}</span>""")
                appendLine("""            <span class="group-count">(${featureNodes.size})</span>""")
                appendLine("""          </div>""")
                appendLine("""          <div class="feature-body collapsible-body">""")

                for (node in featureNodes.sortedBy { it.displayName }) {
                    val nid = htmlId(node.qualifiedName)
                    val incoming = dedup(incomingByDest[node.qualifiedName] ?: emptyList())
                    val outgoing = dedup(outgoingByDest[node.qualifiedName] ?: emptyList())

                    appendLine("""            <div class="screen" id="card-$nid" data-dest="$nid">""")
                    appendLine("""              <div class="screen-caption"><span class="screen-name">${esc(node.displayName)}</span>""")
                    if (node.isShellActive) appendLine("""                <span class="badge badge-shell">shell</span>""")
                    if (node.isWithResult) appendLine("""                <span class="badge badge-result">WithResult</span>""")
                    if (node.navigationPath != null) appendLine("""                <span class="screen-path">${esc(node.navigationPath)}</span>""")
                    appendLine("""              </div>""")
                    appendLine("""              <div class="screen-toolbar">""")
                    if (node.variants.size > 1) {
                        appendLine("""                <select class="variant-select" data-card-id="card-$nid">""")
                        for ((vi, v) in node.variants.withIndex()) {
                            val selected = if (vi == node.defaultVariantIndex) " selected" else ""
                            appendLine("""                  <option value="$vi"$selected>${esc(v.label)}</option>""")
                        }
                        appendLine("""                </select>""")
                    }
                    appendLine("""                <button class="annotate-btn" data-dest="$nid">&#9998;</button>""")
                    appendLine("""                <span class="annotation-marker" id="marker-$nid" hidden>&#9679;</span>""")
                    appendLine("""              </div>""")

                    appendLine("""              <div class="screen-row">""")
                    appendLine("""                <svg class="connectors" id="conn-${node.qualifiedName.hashCode().toUInt()}"></svg>""")
                    appendLine("""                <div class="gutter gutter-in">""")
                    for (ref in incoming) {
                        val cls = when (ref.kind) { EdgeKind.CHROME -> " ref-chrome"; EdgeKind.RESULT -> " ref-result"; else -> "" }
                        val kindAttr = when (ref.kind) { EdgeKind.CHROME -> "chrome"; EdgeKind.RESULT -> "result"; else -> "open" }
                        val rid = htmlId(ref.dest)
                        appendLine("""                  <a class="ref${cls}" data-dest="$rid" data-kind="$kindAttr" title="${esc(ref.display)}">${refThumb(ref.dest)}<span class="ref-label">${esc(ref.display)}</span></a>""")
                    }
                    appendLine("""                </div>""")
                    if (node.variants.isNotEmpty()) {
                        val defaultVar = node.variants.getOrElse(node.defaultVariantIndex) { node.variants[0] }
                        appendLine("""                <div class="screen-img"><img class="snapshot" src="images/${esc(defaultVar.imagePath)}" data-variants='${variantsJson(node)}' alt="${esc(node.displayName)}" loading="lazy" /></div>""")
                    } else {
                        appendLine("""                <div class="screen-img no-snapshot"><span>No snapshot</span></div>""")
                    }
                    appendLine("""                <div class="gutter gutter-out">""")
                    for (ref in outgoing) {
                        val cls = when (ref.kind) { EdgeKind.CHROME -> " ref-chrome"; EdgeKind.RESULT -> " ref-result"; else -> "" }
                        val kindAttr = when (ref.kind) { EdgeKind.CHROME -> "chrome"; EdgeKind.RESULT -> "result"; else -> "open" }
                        val rid = htmlId(ref.dest)
                        appendLine("""                  <a class="ref${cls}" data-dest="$rid" data-kind="$kindAttr" title="${esc(ref.display)}"><span class="ref-label">${esc(ref.display)}</span>${refThumb(ref.dest)}</a>""")
                    }
                    appendLine("""                </div>""")
                    appendLine("""              </div>""")
                    appendLine("""            </div>""")
                }
                appendLine("""          </div>""")
                appendLine("""        </div>""")
            }
            appendLine("""      </div>""")
            appendLine("""    </div>""")
        }

        if (syntheticNodes.isNotEmpty()) {
            val syntheticByPkg = syntheticNodes.groupBy { it.featureGroup }.toSortedMap()
            appendLine("""    <div class="module-group">""")
            appendLine("""      <div class="module-header collapsed" data-collapse-key="mod-standalone">""")
            appendLine("""        <span class="chevron">&#9662;</span>""")
            appendLine("""        <span class="module-name">Standalone surfaces</span>""")
            appendLine("""        <span class="group-count">(${syntheticNodes.size})</span>""")
            appendLine("""      </div>""")
            appendLine("""      <div class="module-body collapsible-body">""")

            for ((pkgGroup, pkgNodes) in syntheticByPkg) {
                val featureKey = "standalone--${pkgGroup.replace('.', '-')}"
                appendLine("""        <div class="feature-group">""")
                appendLine("""          <div class="feature-header" data-collapse-key="feat-$featureKey">""")
                appendLine("""            <span class="chevron">&#9662;</span>""")
                appendLine("""            <span class="feature-name">${esc(pkgGroup)}</span>""")
                appendLine("""            <span class="group-count">(${pkgNodes.size})</span>""")
                appendLine("""          </div>""")
                appendLine("""          <div class="feature-body collapsible-body">""")

                for (node in pkgNodes.sortedBy { it.displayName }) {
                    val nid = htmlId(node.qualifiedName)
                    appendLine("""            <div class="screen" id="card-$nid" data-dest="$nid">""")
                    appendLine("""              <div class="screen-caption"><span class="screen-name">${esc(node.displayName)}</span>""")
                    appendLine("""                <span class="badge badge-standalone">standalone</span>""")
                    appendLine("""                <span class="screen-path">${esc(node.packageName)}</span>""")
                    appendLine("""              </div>""")
                    appendLine("""              <div class="screen-toolbar">""")
                    if (node.variants.size > 1) {
                        appendLine("""                <select class="variant-select" data-card-id="card-$nid">""")
                        for ((vi, v) in node.variants.withIndex()) {
                            val selected = if (vi == node.defaultVariantIndex) " selected" else ""
                            appendLine("""                  <option value="$vi"$selected>${esc(v.label)}</option>""")
                        }
                        appendLine("""                </select>""")
                    }
                    appendLine("""                <button class="annotate-btn" data-dest="$nid">&#9998;</button>""")
                    appendLine("""                <span class="annotation-marker" id="marker-$nid" hidden>&#9679;</span>""")
                    appendLine("""              </div>""")
                    if (node.variants.isNotEmpty()) {
                        val defaultVar = node.variants.getOrElse(node.defaultVariantIndex) { node.variants[0] }
                        appendLine("""              <div class="screen-img"><img class="snapshot" src="images/${esc(defaultVar.imagePath)}" data-variants='${variantsJson(node)}' alt="${esc(node.displayName)}" loading="lazy" /></div>""")
                    } else {
                        appendLine("""              <div class="screen-img no-snapshot"><span>No snapshot</span></div>""")
                    }
                    appendLine("""            </div>""")
                }
                appendLine("""          </div>""")
                appendLine("""        </div>""")
            }
            appendLine("""      </div>""")
            appendLine("""    </div>""")
        }

        appendLine("""  </div>""")
        appendLine("""</div>""")

        appendLine("""<div id="annotation-panel" class="annotation-panel" hidden>""")
        appendLine("""  <div class="annotation-header"><span id="annotation-title">Annotate</span><button id="annotation-close" class="ctrl-btn">X</button></div>""")
        appendLine("""  <textarea id="annotation-text" class="annotation-text" placeholder="Add a note..."></textarea>""")
        appendLine("""  <div style="display:flex;gap:8px;"><button id="annotation-save" class="ctrl-btn">Save</button><button id="annotation-delete" class="ctrl-btn ctrl-btn-danger">Delete</button></div>""")
        appendLine("""</div>""")

        appendLine("""<div id="notes-browser" class="notes-browser" hidden>""")
        appendLine("""  <div class="notes-browser-header">""")
        appendLine("""    <span class="notes-browser-title">Notes</span>""")
        appendLine("""    <span id="notes-pos" class="notes-pos"></span>""")
        appendLine("""    <button id="notes-prev" class="ctrl-btn" title="Previous (p)">&#9664;</button>""")
        appendLine("""    <button id="notes-next" class="ctrl-btn" title="Next (n)">&#9654;</button>""")
        appendLine("""    <button id="notes-browser-close" class="ctrl-btn">X</button>""")
        appendLine("""  </div>""")
        appendLine("""  <div id="notes-list" class="notes-list"></div>""")
        appendLine("""  <div class="notes-browser-footer"><button id="annotation-clear-all" class="ctrl-btn ctrl-btn-danger">Clear all notes</button></div>""")
        appendLine("""</div>""")

        appendLine("""<details class="footer-panel" id="appendix-panel">""")
        appendLine("""  <summary class="footer-toggle">Appendix (unresolved edges, unmatched goldens, zero-golden screens)</summary>""")
        appendLine("""  <div class="footer-content">""")
        if (manifest.unresolvedEdges.isNotEmpty()) {
            appendLine("""    <h3>Unresolved edges (${manifest.unresolvedEdges.size})</h3>""")
            appendLine("""    <table class="appendix-table"><thead><tr><th>File</th><th>Line</th><th>Text</th></tr></thead><tbody>""")
            for (ue in manifest.unresolvedEdges.take(100)) appendLine("""      <tr><td>${esc(ue.file)}</td><td>${ue.line}</td><td>${esc(ue.text)}</td></tr>""")
            appendLine("""    </tbody></table>""")
        }
        val zeroGoldens = manifest.nodes.filter { it.variants.isEmpty() }
        if (zeroGoldens.isNotEmpty()) {
            appendLine("""    <h3>Screens with zero goldens (${zeroGoldens.size})</h3><ul>""")
            for (z in zeroGoldens) appendLine("""      <li>${esc(z.screenName)} (${esc(z.featureGroup)})</li>""")
            appendLine("""    </ul>""")
        }
        if (manifest.unmatchedGoldens > 0) appendLine("""    <p>${manifest.unmatchedGoldens} golden files did not match any screen.</p>""")
        appendLine("""  </div>""")
        appendLine("""</details>""")

        appendLine("""<script>""")
        appendLine("const MANIFEST = $manifestJson;")
        appendLine(generateJs())
        appendLine("""</script>""")
    }
}

private fun variantsJson(node: AtlasNode): String = buildString {
    append("[")
    for ((i, v) in node.variants.withIndex()) {
        if (i > 0) append(",")
        append("""{"label":"${escJson(v.label)}","imagePath":"images/${escJson(v.imagePath)}","width":${v.width},"height":${v.height}}""")
    }
    append("]")
}

private fun escJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

private fun esc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

fun writeHtml(config: ResolvedAtlasConfig, manifest: AtlasManifest) {
    config.outputDir.mkdirs()
    File(config.outputDir, "index.html").writeText(generateHtml(config, manifest))
}

private fun generateCss(): String = """
:root {
  --bg: #fafafa; --ink: #111; --ink-muted: #555; --ink-dim: #999;
  --line: rgba(0,0,0,0.06); --line-strong: rgba(0,0,0,0.14);
  --accent: #333; --accent-soft: #eee;
  --radius: 6px;
  --font-body: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  --font-mono: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  --img-w: 600px; --grid-size: 32px; --grid-color: #f0f0f0;
  --thumb-w: 144px;
}
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: var(--font-body); background: var(--bg); color: var(--ink); font-size: 16px; overflow: hidden; height: 100vh; display: flex; flex-direction: column; }

.atlas-header { display: flex; align-items: center; gap: 20px; padding: 12px 20px; background: #fff; border-bottom: 1px solid var(--line-strong); flex-shrink: 0; z-index: 100; flex-wrap: wrap; }
.atlas-title { font-family: var(--font-body); font-size: 22px; font-weight: 600; letter-spacing: -0.3px; }
.atlas-stats { display: flex; gap: 14px; font-family: var(--font-mono); font-size: 14px; color: var(--ink-muted); }
.stat { white-space: nowrap; }
.stat-warn { color: var(--ink); font-weight: 600; cursor: pointer; text-decoration: none; }
.stat-warn:hover { text-decoration: underline; }
.atlas-controls { display: flex; gap: 8px; margin-left: auto; }
.atlas-timestamp { font-family: var(--font-mono); font-size: 12px; color: var(--ink-dim); }
.search-box { padding: 6px 12px; border-radius: 6px; border: 1px solid var(--line-strong); background: var(--bg); color: var(--ink); font-family: var(--font-body); font-size: 14px; width: 200px; outline: none; }
.search-box:focus { border-color: var(--ink); }
.search-results { position: absolute; top: 56px; right: 250px; z-index: 200; background: #fff; border: 1px solid var(--line-strong); border-radius: 8px; max-height: 360px; overflow-y: auto; min-width: 280px; box-shadow: 0 8px 24px rgba(0,0,0,0.12); }
.search-results .search-item { padding: 10px 14px; cursor: pointer; font-size: 15px; border-bottom: 1px solid var(--line); }
.search-results .search-item:hover { background: var(--bg); }
.search-results .search-item .search-feature { font-family: var(--font-mono); font-size: 12px; color: var(--ink-dim); margin-left: 8px; }
.ctrl-btn { padding: 6px 12px; border-radius: 6px; border: 1px solid var(--line-strong); background: #fff; color: var(--ink); font-family: var(--font-body); font-size: 14px; cursor: pointer; white-space: nowrap; }
.ctrl-btn:hover { background: var(--bg); }
.ctrl-btn-danger { color: #c00; }
.ctrl-btn-danger:hover { background: #fee; }

.viewport { flex: 1; overflow: hidden; position: relative; cursor: grab; background-color: var(--bg); background-image: linear-gradient(var(--grid-color) 1px, transparent 1px), linear-gradient(90deg, var(--grid-color) 1px, transparent 1px); background-size: var(--grid-size) var(--grid-size); }
.viewport.dragging { cursor: grabbing; }
.world { position: absolute; top: 0; left: 0; transform-origin: 0 0; will-change: transform; padding: 48px 60px 160px; min-width: 100%; }

.module-group { margin-bottom: 20px; }
.module-header, .feature-header { display: flex; align-items: center; gap: 10px; cursor: pointer; user-select: none; padding: 8px 10px; border-radius: 6px; }
.module-header:hover, .feature-header:hover { background: var(--line); }
.module-header { font-family: var(--font-mono); font-size: 22px; font-weight: 600; color: var(--ink); border-bottom: 1px solid var(--line-strong); border-radius: 0; padding: 14px 10px; margin-bottom: 4px; }
.feature-header { font-family: var(--font-body); font-size: 22px; font-weight: 600; color: var(--ink-muted); padding-left: 24px; }
.chevron { display: inline-block; font-size: 16px; transition: transform 0.15s ease; color: var(--ink-dim); width: 16px; text-align: center; }
.collapsed > .chevron { transform: rotate(-90deg); }
.group-count { font-family: var(--font-mono); font-size: 16px; color: var(--ink-dim); font-weight: 400; }
.collapsible-body { overflow: hidden; }
.collapsed + .collapsible-body { display: none; }

.screen { margin: 40px 0; }
.screen.focused .screen-caption { color: var(--ink); font-style: italic; }
.screen-caption { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; flex-wrap: wrap; }
.screen-name { font-family: var(--font-body); font-size: 22px; font-weight: 600; letter-spacing: -0.2px; color: var(--ink); }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-family: var(--font-mono); font-size: 12px; font-weight: 500; letter-spacing: 0.3px; white-space: nowrap; }
.badge-shell { background: rgba(0,0,0,0.06); color: var(--ink-muted); }
.badge-result { background: rgba(0,0,0,0.06); color: var(--ink-muted); }
.badge-standalone { background: rgba(0,0,0,0.06); color: var(--ink-dim); }
.screen-path { font-family: var(--font-mono); font-size: 14px; color: var(--ink-dim); word-break: break-all; }
.screen-toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.variant-select { padding: 4px 8px; border-radius: 5px; border: 1px solid var(--line-strong); background: #fff; font-size: 14px; font-family: var(--font-body); color: var(--ink); max-width: 220px; }
.annotate-btn { width: 32px; height: 32px; border-radius: 16px; border: 1px solid var(--line); background: transparent; color: var(--ink-dim); font-size: 16px; cursor: pointer; display: flex; align-items: center; justify-content: center; margin-left: auto; flex-shrink: 0; }
.annotate-btn:hover { background: var(--accent-soft); color: var(--ink); }
.annotation-marker { color: var(--ink); font-size: 14px; flex-shrink: 0; }

.screen-row { display: flex; align-items: flex-start; position: relative; }
.connectors { position: absolute; inset: 0; pointer-events: none; overflow: visible; }
.conn-edge { fill: none; stroke: var(--ink-dim); stroke-width: 1.5; opacity: 0.3; }
.conn-edge.kind-result { stroke: var(--ink-dim); stroke-dasharray: 6,4; }
.conn-edge.kind-chrome { stroke: var(--ink-dim); stroke-dasharray: 2,3; opacity: 0.18; }

.gutter { position: relative; flex-shrink: 0; overflow: visible; min-width: 40px; }
.ref { position: absolute; display: flex; align-items: center; gap: 8px; cursor: pointer; text-decoration: none; font-family: var(--font-mono); color: var(--ink-muted); line-height: 1.4; white-space: nowrap; }
.ref:hover { color: var(--ink); }
.ref:hover .ref-thumb { border-color: var(--ink-muted); }
.ref-chrome { color: var(--ink-dim); font-style: italic; }
.ref-chrome:hover { color: var(--ink-muted); }
.ref-chrome:hover .ref-thumb { border-color: var(--ink-dim); }
.ref-label { font-size: 14px; }
.ref-thumb { width: var(--thumb-w); flex-shrink: 0; display: block; border: 1px solid #e0e0e0; border-radius: 2px; }
.ref-thumb-placeholder { width: var(--thumb-w); height: 80px; flex-shrink: 0; display: block; background: #fff; border: 1px dashed var(--line-strong); border-radius: 2px; }

.screen-img { flex-shrink: 0; }
.screen-img img { display: block; max-height: 600px; object-fit: contain; object-position: top; border: 1px solid #ddd; box-shadow: 0 1px 4px rgba(0,0,0,0.08); border-radius: 3px; }
.no-snapshot { width: 200px; height: 100px; display: flex; align-items: center; justify-content: center; border: 1px dashed var(--line-strong); border-radius: 6px; background: #fff; color: var(--ink-dim); font-family: var(--font-mono); font-size: 16px; }

.annotation-panel { position: fixed; top: 80px; right: 20px; width: 340px; background: #fff; border: 1px solid var(--line-strong); border-radius: var(--radius); padding: 20px; z-index: 300; box-shadow: 0 12px 40px rgba(0,0,0,0.15); }
.annotation-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-family: var(--font-body); font-size: 18px; font-weight: 600; }
.annotation-text { width: 100%; height: 100px; border: 1px solid var(--line-strong); border-radius: 6px; padding: 10px; font-family: var(--font-body); font-size: 14px; color: var(--ink); background: var(--bg); resize: vertical; margin-bottom: 10px; }
.notes-browser { position: fixed; top: 80px; left: 20px; width: 360px; max-height: 60vh; background: #fff; border: 1px solid var(--line-strong); border-radius: var(--radius); z-index: 300; box-shadow: 0 12px 40px rgba(0,0,0,0.15); display: flex; flex-direction: column; }
.notes-browser[hidden] { display: none; }
.notes-browser-header { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-bottom: 1px solid var(--line); flex-shrink: 0; }
.notes-browser-title { font-family: var(--font-body); font-size: 16px; font-weight: 600; }
.notes-pos { font-family: var(--font-mono); font-size: 12px; color: var(--ink-dim); margin-left: auto; }
.notes-list { overflow-y: auto; flex: 1; }
.note-entry { padding: 10px 16px; border-bottom: 1px solid var(--line); cursor: pointer; }
.note-entry:hover { background: var(--bg); }
.note-entry.note-active { background: #e8e8e8; }
.note-entry-screen { font-family: var(--font-body); font-size: 13px; font-weight: 600; color: var(--ink); }
.note-entry-variant { font-family: var(--font-mono); font-size: 11px; color: var(--ink-dim); margin-left: 6px; }
.note-entry-text { font-family: var(--font-body); font-size: 12px; color: var(--ink-muted); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.note-entry.note-stale { opacity: 0.4; text-decoration: line-through; cursor: default; }
.notes-browser-footer { padding: 10px 16px; border-top: 1px solid var(--line); flex-shrink: 0; }
.footer-panel { flex-shrink: 0; border-top: 1px solid var(--line-strong); background: #fff; max-height: 40vh; overflow-y: auto; z-index: 50; position: relative; }
.footer-toggle { padding: 12px 20px; font-family: var(--font-mono); font-size: 14px; color: var(--ink-muted); cursor: pointer; list-style: none; }
.footer-toggle::-webkit-details-marker { display: none; }
.footer-content { padding: 0 20px 20px; }
.footer-content h3 { font-family: var(--font-body); font-size: 18px; font-weight: 600; margin: 20px 0 10px; }
.appendix-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.appendix-table th, .appendix-table td { text-align: left; padding: 6px 10px; border-bottom: 1px solid var(--line); }
.appendix-table th { font-family: var(--font-mono); font-size: 12px; color: var(--ink-dim); text-transform: uppercase; letter-spacing: 0.5px; }
.appendix-table td { font-family: var(--font-mono); color: var(--ink-muted); max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.footer-content ul { padding-left: 20px; }
.footer-content li { font-size: 14px; color: var(--ink-muted); margin-bottom: 4px; }
.footer-content p { font-size: 14px; color: var(--ink-muted); margin-top: 10px; }
"""

private fun generateJs(): String = """
(function() {
  var GRID = 32, NS = 'http://www.w3.org/2000/svg';
  var THUMB_W = 144, CURVE_GAP = 140, TN_GAP = 12, REF_GAP = 10;
  var viewport = document.getElementById('viewport');
  var world = document.getElementById('world');
  var searchInput = document.getElementById('search-input');
  var searchResults = document.getElementById('search-results');
  var scale = 1, panX = 0, panY = 0, isDrag = false, lastX = 0, lastY = 0;

  function applyTransform() {
    world.style.transform = 'translate('+panX+'px,'+panY+'px) scale('+scale+')';
    var gs = GRID*scale;
    viewport.style.backgroundSize = gs+'px '+gs+'px';
    viewport.style.backgroundPosition = panX+'px '+panY+'px';
  }
  viewport.addEventListener('mousedown', function(e) {
    if (e.target.closest('select,button,.ref,a,input,textarea')) return;
    isDrag=true; lastX=e.clientX; lastY=e.clientY;
    viewport.classList.add('dragging'); e.preventDefault();
  });
  window.addEventListener('mousemove', function(e) {
    if (!isDrag) return;
    panX+=e.clientX-lastX; panY+=e.clientY-lastY;
    lastX=e.clientX; lastY=e.clientY; applyTransform();
  });
  window.addEventListener('mouseup', function() { if(isDrag) savePanZoom(); isDrag=false; viewport.classList.remove('dragging'); });
  viewport.addEventListener('wheel', function(e) {
    if (e.ctrlKey||e.metaKey) {
      e.preventDefault();
      var r=viewport.getBoundingClientRect(), mx=e.clientX-r.left, my=e.clientY-r.top;
      var wx=(mx-panX)/scale, wy=(my-panY)/scale;
      scale=Math.max(0.1,Math.min(5,scale*Math.exp(-e.deltaY*0.004)));
      panX=mx-wx*scale; panY=my-wy*scale;
    } else { e.preventDefault(); panX-=e.deltaX; panY-=e.deltaY; }
    applyTransform(); debounceSave();
  }, {passive:false});

  var IMG_H = 600;
  function singleScreenScale() {
    var vh = viewport.clientHeight;
    return Math.min((vh * 0.75) / IMG_H, 1);
  }

  function zoomToFit() {
    var vw=viewport.clientWidth, vh=viewport.clientHeight;
    var ww=world.scrollWidth, wh=world.scrollHeight;
    if (!ww||!wh) return;
    scale=Math.min(vw/ww,vh/wh,1);
    panX=(vw-ww*scale)/2; panY=Math.max(0,(vh-wh*scale)/2);
    applyTransform(); savePanZoom();
  }

  function zoomSingleScreen() {
    var s = singleScreenScale();
    var vr = viewport.getBoundingClientRect();
    var cx = vr.width/2, cy = vr.height/2;
    var wx = (cx-panX)/scale, wy = (cy-panY)/scale;
    scale = s;
    panX = cx - wx*scale; panY = cy - wy*scale;
    applyTransform(); savePanZoom();
  }

  function zoomDefault() {
    scale = singleScreenScale();
    panX = 0; panY = 0;
    applyTransform(); savePanZoom();
  }

  var PZK = 'ui-atlas-panzoom';
  function savePanZoom() {
    try { localStorage.setItem(PZK, JSON.stringify({s:scale,x:panX,y:panY})); } catch(e) {}
  }
  function loadPanZoom() {
    try {
      var d = JSON.parse(localStorage.getItem(PZK));
      if (d && typeof d.s === 'number') { scale=d.s; panX=d.x; panY=d.y; return true; }
    } catch(e) {}
    return false;
  }

  var saveTimer = null;
  function debounceSave() { clearTimeout(saveTimer); saveTimer = setTimeout(savePanZoom, 300); }

  document.getElementById('zoom-fit-btn').addEventListener('click', zoomToFit);
  document.getElementById('zoom-single-btn').addEventListener('click', zoomSingleScreen);
  document.getElementById('zoom-in-btn').addEventListener('click', function() {
    var r=viewport.getBoundingClientRect(),cx=r.width/2,cy=r.height/2;
    var wx=(cx-panX)/scale,wy=(cy-panY)/scale;
    scale=Math.min(5,scale*1.3);panX=cx-wx*scale;panY=cy-wy*scale;applyTransform();savePanZoom();
  });
  document.getElementById('zoom-out-btn').addEventListener('click', function() {
    var r=viewport.getBoundingClientRect(),cx=r.width/2,cy=r.height/2;
    var wx=(cx-panX)/scale,wy=(cy-panY)/scale;
    scale=Math.max(0.1,scale/1.3);panX=cx-wx*scale;panY=cy-wy*scale;applyTransform();savePanZoom();
  });

  var CK='ui-atlas-collapsed';
  function ldC(){try{return JSON.parse(localStorage.getItem(CK)||'{}');}catch(e){return {};}}
  function svC(d){localStorage.setItem(CK,JSON.stringify(d));}
  var cs=ldC();
  document.querySelectorAll('.module-header,.feature-header').forEach(function(h){
    var k=h.dataset.collapseKey;
    if(cs[k]) h.classList.add('collapsed');
    h.addEventListener('click',function(){
      h.classList.toggle('collapsed');
      var s=ldC();if(h.classList.contains('collapsed'))s[k]=true;else delete s[k];svC(s);
      scheduleLayout();
    });
  });

  document.querySelectorAll('.variant-select').forEach(function(sel){
    sel.addEventListener('change',function(){
      var card=document.getElementById(sel.dataset.cardId);
      if(!card) return;
      var img=card.querySelector('.snapshot');
      if(!img) return;
      var vs=JSON.parse(img.dataset.variants);
      var v=vs[parseInt(sel.value)];
      if(v){img.src=v.imagePath; scheduleLayout();}
    });
  });

  var globalLeftW = 0, globalRightW = 0;
  var globalLeftNameW = 0, globalRightNameW = 0;
  var globalLeftContent = 0, globalRightContent = 0;

  function measureGlobals() {
    globalLeftNameW = 0; globalRightNameW = 0;
    document.querySelectorAll('.screen-row').forEach(function(row) {
      row.querySelectorAll('.gutter-in .ref .ref-label').forEach(function(l) {
        globalLeftNameW = Math.max(globalLeftNameW, l.scrollWidth);
      });
      row.querySelectorAll('.gutter-out .ref .ref-label').forEach(function(l) {
        globalRightNameW = Math.max(globalRightNameW, l.scrollWidth);
      });
    });
    globalLeftContent = THUMB_W + TN_GAP + globalLeftNameW;
    globalRightContent = THUMB_W + TN_GAP + globalRightNameW;
    globalLeftW = globalLeftContent > THUMB_W ? globalLeftContent + CURVE_GAP : 40;
    globalRightW = globalRightContent > THUMB_W ? globalRightContent + CURVE_GAP : 40;
  }

  function layoutAll() {
    measureGlobals();
    document.querySelectorAll('.screen-row').forEach(function(row) { try { layoutRow(row); } catch(e) {} });
  }

  function layoutRow(row) {
    var svg = row.querySelector('.connectors');
    if (svg) svg.innerHTML = '';

    var imgEl = row.querySelector('.screen-img');
    if (!imgEl) return;
    var imgH = imgEl.offsetHeight || 100;
    var imgCY = imgH / 2;

    var screen = row.closest('.screen');
    screen.style.paddingTop = '';

    var caption = screen.querySelector('.screen-caption');
    var toolbar = screen.querySelector('.screen-toolbar');

    function layoutGutter(gutter, side) {
      var isIn = side === 'in';
      var gutterW = isIn ? globalLeftW : globalRightW;
      var contentW = isIn ? globalLeftContent : globalRightContent;
      var nameW = isIn ? globalLeftNameW : globalRightNameW;
      gutter.style.width = gutterW + 'px';

      var refs = Array.from(gutter.querySelectorAll('.ref'));
      if (!refs.length) return 0;

      var refData = refs.map(function(ref) {
        var thumb = ref.querySelector('.ref-thumb, .ref-thumb-placeholder');
        var label = ref.querySelector('.ref-label');
        var th = thumb ? thumb.offsetHeight : 80;
        if (th < 20) th = 80;
        if (label) { label.style.width = nameW + 'px'; label.style.flexShrink = '0'; }
        return { ref: ref, h: th };
      });

      var totalH = 0;
      refData.forEach(function(d, i) { totalH += d.h; if (i > 0) totalH += REF_GAP; });
      var startY = imgCY - totalH / 2;

      var curY = startY;
      refData.forEach(function(d) {
        d.ref.style.top = curY + 'px';
        d.ref.style.height = d.h + 'px';
        d.ref.style.width = contentW + 'px';
        d.ref.style.flexDirection = 'row';
        if (isIn) {
          d.ref.style.right = CURVE_GAP + 'px';
          d.ref.style.left = 'auto';
        } else {
          d.ref.style.left = CURVE_GAP + 'px';
          d.ref.style.right = 'auto';
        }
        curY += d.h + REF_GAP;
      });

      gutter.style.height = Math.max(imgH, totalH) + 'px';
      return startY;
    }

    var gutterIn = row.querySelector('.gutter-in');
    var gutterOut = row.querySelector('.gutter-out');

    var startIn = gutterIn ? layoutGutter(gutterIn, 'in') : 0;
    var startOut = gutterOut ? layoutGutter(gutterOut, 'out') : 0;

    gutterIn.style.width = globalLeftW + 'px';
    gutterOut.style.width = globalRightW + 'px';

    var leftW = globalLeftW, imgW = imgEl.offsetWidth || 600;
    if (caption) { caption.style.paddingLeft = leftW + 'px'; caption.style.maxWidth = (leftW + imgW) + 'px'; }
    if (toolbar) { toolbar.style.paddingLeft = leftW + 'px'; toolbar.style.maxWidth = (leftW + imgW) + 'px'; }

    var worstOverflow = Math.max(0, -startIn, -startOut);
    if (worstOverflow > 0) {
      screen.style.paddingTop = Math.ceil(worstOverflow) + 'px';
    }

    if (!svg) return;
    var rw = row.scrollWidth, rh = row.scrollHeight;
    svg.setAttribute('width', rw); svg.setAttribute('height', rh);
    svg.style.width = rw+'px'; svg.style.height = rh+'px';

    var defs = document.createElementNS(NS,'defs');
    var mk = document.createElementNS(NS,'marker');
    mk.setAttribute('id','ah-'+svg.id); mk.setAttribute('markerWidth','7');
    mk.setAttribute('markerHeight','5'); mk.setAttribute('refX','7');
    mk.setAttribute('refY','2.5'); mk.setAttribute('orient','auto');
    var pg = document.createElementNS(NS,'polygon');
    pg.setAttribute('points','0 0, 7 2.5, 0 5');
    pg.setAttribute('fill','#a8a29e'); pg.setAttribute('opacity','0.5');
    mk.appendChild(pg); defs.appendChild(mk); svg.appendChild(defs);
    var mid = 'url(#ah-'+svg.id+')';

    function lo(el) {
      var x=0,y=0,c=el;
      while(c&&c!==row){x+=c.offsetLeft;y+=c.offsetTop;c=c.offsetParent;}
      return {x:x,y:y};
    }
    var ip = lo(imgEl);
    var imgL = ip.x, imgR = ip.x + imgEl.offsetWidth;
    var imgTop = ip.y, imgBot = ip.y + imgEl.offsetHeight;

    function drawCurve(sx,sy,tx,ty,kind) {
      var cpx=Math.abs(tx-sx)*0.45;
      var p=document.createElementNS(NS,'path');
      var c1x=sx<tx?sx+cpx:sx-cpx, c2x=sx<tx?tx-cpx:tx+cpx;
      p.setAttribute('d','M'+sx+','+sy+' C'+c1x+','+sy+' '+c2x+','+ty+' '+tx+','+ty);
      var cls='conn-edge';
      if(kind==='result')cls+=' kind-result'; else if(kind==='chrome')cls+=' kind-chrome';
      p.setAttribute('class',cls); p.setAttribute('marker-end',mid);
      svg.appendChild(p);
    }

    var imgMidY = (imgTop + imgBot) / 2;
    var ANCHOR_SPREAD = 12;

    var inRefs = Array.from(row.querySelectorAll('.gutter-in .ref'));
    inRefs.forEach(function(ref, i) {
      var label = ref.querySelector('.ref-label');
      if (!label) return;
      var lp = lo(label);
      var sx = lp.x + label.offsetWidth, sy = lp.y + label.offsetHeight/2;
      var ay = imgMidY + (i - (inRefs.length-1)/2) * ANCHOR_SPREAD;
      ay = Math.max(imgTop+4, Math.min(imgBot-4, ay));
      drawCurve(sx,sy,imgL,ay,ref.dataset.kind);
    });

    var outRefs = Array.from(row.querySelectorAll('.gutter-out .ref'));
    outRefs.forEach(function(ref, i) {
      var label = ref.querySelector('.ref-label');
      if (!label) return;
      var lp = lo(label);
      var tx = lp.x, ty = lp.y + label.offsetHeight/2;
      var ay = imgMidY + (i - (outRefs.length-1)/2) * ANCHOR_SPREAD;
      ay = Math.max(imgTop+4, Math.min(imgBot-4, ay));
      drawCurve(imgR,ay,tx,ty,ref.dataset.kind);
    });
  }

  function scheduleLayout() { requestAnimationFrame(layoutAll); }
  window.addEventListener('load', scheduleLayout);
  document.querySelectorAll('.snapshot').forEach(function(img) { img.addEventListener('load', scheduleLayout); });
  setTimeout(scheduleLayout, 300);
  setTimeout(scheduleLayout, 1500);

  function focusCard(dest) {
    var card = document.getElementById('card-'+dest);
    if (!card) return;
    var el = card.parentElement;
    while (el && el !== world) {
      if (el.classList.contains('collapsible-body')) {
        var hdr = el.previousElementSibling;
        if (hdr && hdr.classList.contains('collapsed')) {
          hdr.classList.remove('collapsed');
          var s=ldC(); delete s[hdr.dataset.collapseKey]; svC(s);
        }
      }
      el = el.parentElement;
    }
    requestAnimationFrame(function() {
      layoutAll();
      var oldScale = scale;
      var vr=viewport.getBoundingClientRect(), cr=card.getBoundingClientRect();
      var wx=(cr.left-vr.left-panX)/oldScale, wy=(cr.top-vr.top-panY)/oldScale;
      var ww=cr.width/oldScale, wh=cr.height/oldScale;
      scale = singleScreenScale();
      panX=vr.width/2-(wx+ww/2)*scale; panY=vr.height/2-(wy+wh/2)*scale;
      applyTransform(); savePanZoom();
      card.classList.add('focused');
      setTimeout(function(){card.classList.remove('focused');},1500);
    });
  }
  document.querySelectorAll('.ref').forEach(function(r){
    r.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();focusCard(r.dataset.dest);});
  });

  function toHtmlId(qn) { return qn.replace(/\./g, '_'); }

  searchInput.addEventListener('input',function(){
    var q=searchInput.value.toLowerCase().trim();
    if(q.length<2){searchResults.hidden=true;return;}
    var m=MANIFEST.nodes.filter(function(n){
      return n.displayName.toLowerCase().includes(q)||n.screenName.toLowerCase().includes(q)||n.destinationName.toLowerCase().includes(q);
    }).slice(0,15);
    if(!m.length){searchResults.hidden=true;return;}
    searchResults.innerHTML=''; searchResults.hidden=false;
    m.forEach(function(n){
      var d=document.createElement('div');d.className='search-item';
      d.innerHTML=n.displayName+'<span class="search-feature">'+n.featureGroup+'</span>';
      d.addEventListener('click',function(){searchResults.hidden=true;searchInput.value='';focusCard(toHtmlId(n.qualifiedName));});
      searchResults.appendChild(d);
    });
  });
  searchInput.addEventListener('blur',function(){setTimeout(function(){searchResults.hidden=true;},200);});

  var AK='ui-atlas-annotations';
  function ldA(){try{return JSON.parse(localStorage.getItem(AK)||'{}');}catch(e){return {};}}
  function svA(d){localStorage.setItem(AK,JSON.stringify(d));}
  function rfM(){
    var d=ldA();
    MANIFEST.nodes.forEach(function(n){
      var mk=document.getElementById('marker-'+toHtmlId(n.qualifiedName));
      if(mk) mk.hidden=!Object.keys(d).some(function(k){return k.startsWith(n.qualifiedName+'|')&&d[k].note;});
    });
  }
  rfM();
  var caDest=null,caVar=null;
  document.querySelectorAll('.annotate-btn').forEach(function(btn){
    btn.addEventListener('click',function(e){
      e.stopPropagation();
      var dest=btn.dataset.dest, scr=btn.closest('.screen');
      var sel=scr?scr.querySelector('.variant-select'):null;
      caVar=sel?sel.options[sel.selectedIndex].text:'Default';
      caDest=dest;
      document.getElementById('annotation-title').textContent=dest+' / '+caVar;
      var d=ldA();
      document.getElementById('annotation-text').value=(d[dest+'|'+caVar]&&d[dest+'|'+caVar].note)||'';
      document.getElementById('annotation-panel').hidden=false;
    });
  });
  document.getElementById('annotation-close').addEventListener('click',function(){document.getElementById('annotation-panel').hidden=true;});
  document.getElementById('annotation-save').addEventListener('click',function(){
    if(!caDest) return;
    var d=ldA(),k=caDest+'|'+caVar,note=document.getElementById('annotation-text').value.trim();
    if(note){
      var nd=MANIFEST.nodes.find(function(n){return toHtmlId(n.qualifiedName)===caDest;});
      d[k]={note:note,screen:caDest,feature:nd?nd.featureGroup:'',variant:caVar,updatedAt:new Date().toISOString()};
    } else delete d[k];
    svA(d);rfM();rfNB();document.getElementById('annotation-panel').hidden=true;
  });
  document.getElementById('annotation-delete').addEventListener('click',function(){
    if(!caDest) return;
    var d=ldA(); delete d[caDest+'|'+caVar]; svA(d);
    rfM();rfNB();document.getElementById('annotation-panel').hidden=true;
  });
  document.getElementById('annotation-clear-all').addEventListener('click',function(){
    if(!confirm('Clear ALL annotations?')) return;
    localStorage.removeItem(AK);rfM();rfNB();document.getElementById('annotation-panel').hidden=true;
  });
  document.getElementById('export-csv-btn').addEventListener('click',function(){
    var d=ldA(),ks=Object.keys(d);
    if(!ks.length){alert('No annotations to export.');return;}
    var csv='screen,feature,variant,note,updatedAt\n';
    ks.forEach(function(k){var r=d[k];csv+=[r.screen,r.feature,r.variant,r.note,r.updatedAt].map(ce).join(',')+'\n';});
    var b=new Blob([csv],{type:'text/csv'}),u=URL.createObjectURL(b);
    var a=document.createElement('a');a.href=u;a.download='ui-atlas-annotations.csv';
    document.body.appendChild(a);a.click();document.body.removeChild(a);URL.revokeObjectURL(u);
  });
  function ce(s){if(!s)return'';return(s.includes(',')||s.includes('"')||s.includes('\n'))?'"'+s.replace(/"/g,'""')+'"':s;}

  function rfNB(){if(!notesBrowser.hidden) buildNoteList();}
  var notesBrowser=document.getElementById('notes-browser');
  var notesList=document.getElementById('notes-list');
  var notesPos=document.getElementById('notes-pos');
  var notesCount=document.getElementById('notes-count');
  var noteIdx=-1, noteKeys=[];

  function outlineOrder() {
    var order=[];
    document.querySelectorAll('.screen[data-dest]').forEach(function(el){order.push(el.dataset.dest);});
    return order;
  }

  function buildNoteList() {
    var d=ldA(), order=outlineOrder();
    var entries=[];
    Object.keys(d).forEach(function(k){
      var r=d[k]; if(!r||!r.note) return;
      var parts=k.split('|'); var dest=parts[0]; var variant=parts.slice(1).join('|');
      var nd=MANIFEST.nodes.find(function(n){return toHtmlId(n.qualifiedName)===dest;});
      var idx=order.indexOf(dest);
      entries.push({key:k, dest:dest, variant:variant, display:nd?nd.displayName:dest, note:r.note, order:idx>=0?idx:9999});
    });
    entries.sort(function(a,b){return a.order-b.order||a.variant.localeCompare(b.variant);});
    noteKeys=entries;
    notesCount.textContent=entries.length;
    notesList.innerHTML='';
    entries.forEach(function(e,i){
      var stale=!document.getElementById('card-'+e.dest);
      var div=document.createElement('div'); div.className='note-entry'+(stale?' note-stale':''); div.dataset.idx=i;
      div.innerHTML='<div><span class="note-entry-screen">'+e.display+'</span><span class="note-entry-variant">'+e.variant+'</span></div><div class="note-entry-text">'+e.note.split('\n')[0]+'</div>';
      if(!stale) div.addEventListener('click',function(){jumpToNote(i);});
      notesList.appendChild(div);
    });
    if(noteIdx>=entries.length) noteIdx=entries.length-1;
    updateNotePos();
  }

  function updateNotePos() {
    if(!noteKeys.length){notesPos.textContent=''; return;}
    notesPos.textContent=(noteIdx+1)+' / '+noteKeys.length;
    notesList.querySelectorAll('.note-entry').forEach(function(el,i){el.classList.toggle('note-active',i===noteIdx);});
    var active=notesList.querySelector('.note-active');
    if(active) active.scrollIntoView({block:'nearest'});
  }

  function jumpToNote(i) {
    if(i<0||i>=noteKeys.length) return;
    noteIdx=i; updateNotePos();
    var e=noteKeys[i];
    var card=document.getElementById('card-'+e.dest);
    if(!card) return;
    var el=card.parentElement;
    while(el&&el!==world){
      if(el.classList.contains('collapsible-body')){
        var hdr=el.previousElementSibling;
        if(hdr&&hdr.classList.contains('collapsed')){hdr.classList.remove('collapsed');var s=ldC();delete s[hdr.dataset.collapseKey];svC(s);}
      }
      el=el.parentElement;
    }
    requestAnimationFrame(function(){requestAnimationFrame(function(){
      layoutAll();
      var sel=card.querySelector('.variant-select');
      if(sel){
        for(var oi=0;oi<sel.options.length;oi++){
          if(sel.options[oi].text===e.variant){sel.selectedIndex=oi;sel.dispatchEvent(new Event('change'));break;}
        }
      }
      var vr=viewport.getBoundingClientRect(),cr=card.getBoundingClientRect();
      var wx=(cr.left-vr.left-panX)/scale,wy=(cr.top-vr.top-panY)/scale;
      var ww=cr.width/scale,wh=cr.height/scale;
      panX=vr.width/2-(wx+ww/2)*scale;panY=vr.height/2-(wy+wh/2)*scale;
      applyTransform();savePanZoom();
      caDest=e.dest;caVar=e.variant;
      document.getElementById('annotation-title').textContent=e.display+' / '+e.variant;
      var d=ldA();
      document.getElementById('annotation-text').value=(d[e.key]&&d[e.key].note)||'';
      document.getElementById('annotation-panel').hidden=false;
    });});
  }

  function closeNotes(){notesBrowser.hidden=true;}
  document.getElementById('notes-btn').addEventListener('click',function(){
    notesBrowser.hidden=!notesBrowser.hidden;
    if(!notesBrowser.hidden) buildNoteList();
  });
  document.getElementById('notes-browser-close').addEventListener('click',closeNotes);
  document.getElementById('notes-prev').addEventListener('click',function(){if(noteKeys.length) jumpToNote(noteIdx<=0?noteKeys.length-1:noteIdx-1);});
  document.getElementById('notes-next').addEventListener('click',function(){if(noteKeys.length) jumpToNote(noteIdx>=noteKeys.length-1?0:noteIdx+1);});
  viewport.addEventListener('click',function(e){if(!notesBrowser.hidden&&!e.target.closest('.notes-browser')&&!e.target.closest('#notes-btn'))closeNotes();});
  document.addEventListener('keydown',function(e){
    if(e.key==='Escape'&&!notesBrowser.hidden){closeNotes();return;}
    if(notesBrowser.hidden) return;
    if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA'||e.target.tagName==='SELECT') return;
    if(e.key==='n'){e.preventDefault();document.getElementById('notes-next').click();}
    if(e.key==='p'){e.preventDefault();document.getElementById('notes-prev').click();}
  });

  (function migrateAnnotationKeys(){
    var d=ldA(), changed=false;
    var bySimple={};
    MANIFEST.nodes.forEach(function(n){
      var simple=n.destinationName;
      (bySimple[simple]=bySimple[simple]||[]).push(n);
    });
    var newD={};
    Object.keys(d).forEach(function(k){
      var parts=k.split('|'), dest=parts[0], variant=parts.slice(1).join('|');
      if(document.getElementById('card-'+dest)){newD[k]=d[k];return;}
      var candidates=bySimple[dest];
      if(candidates&&candidates.length===1){
        var newDest=toHtmlId(candidates[0].qualifiedName);
        var newKey=newDest+'|'+variant;
        var entry=d[k]; entry.screen=newDest;
        newD[newKey]=entry; changed=true;
      } else { newD[k]=d[k]; }
    });
    if(changed) svA(newD);
  })();
  buildNoteList();

  var ul=document.getElementById('unresolved-link');
  if(ul) ul.addEventListener('click',function(e){
    e.preventDefault();
    var p=document.getElementById('appendix-panel');
    if(p){p.open=true;p.scrollIntoView({behavior:'smooth',block:'end'});}
  });

  setTimeout(function() {
    if (loadPanZoom()) { applyTransform(); }
    else { zoomToFit(); }
  }, 100);
})();
"""
