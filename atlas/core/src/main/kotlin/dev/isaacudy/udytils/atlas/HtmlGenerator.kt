package dev.isaacudy.udytils.atlas

import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textArea
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import java.io.File

private const val CSS_RESOURCE = "/dev/isaacudy/udytils/atlas/atlas.css"
private const val JS_RESOURCE = "/dev/isaacudy/udytils/atlas/atlas.js"

private fun loadResource(path: String): String =
    object {}.javaClass.getResourceAsStream(path)?.bufferedReader()?.readText()
        ?: error("Missing classpath resource: $path")

private data class EdgeRef(val dest: String, val display: String, val kind: EdgeKind)

fun generateHtml(config: ResolvedAtlasConfig, manifest: AtlasManifest): String {
    val json = kotlinx.serialization.json.Json { prettyPrint = true }
    val manifestJson = json.encodeToString(AtlasManifest.serializer(), manifest)

    val cssText = loadResource(CSS_RESOURCE)
    val jsText = loadResource(JS_RESOURCE)

    fun htmlId(qualifiedName: String) = qualifiedName.replace('.', '_')

    val qnToDisplay = manifest.nodes.associate { it.qualifiedName to it.displayName }
    val qnToDefaultImage = manifest.nodes.associate { node ->
        val img = node.variants.getOrElse(node.defaultVariantIndex) { node.variants.firstOrNull() }
        node.qualifiedName to img?.imagePath
    }

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
            group.minByOrNull { when (it.kind) { EdgeKind.RESULT -> 0; EdgeKind.OPEN -> 1; EdgeKind.CHROME -> 2 } }!!
        }.sortedBy { it.display }
    }

    fun refThumbHtml(qn: String): String {
        val imgPath = qnToDefaultImage[qn]
        return if (imgPath != null) {
            """<img class="ref-thumb" src="images/${escHtml(imgPath)}" loading="lazy" />"""
        } else {
            """<span class="ref-thumb-placeholder"></span>"""
        }
    }

    val realNodes = manifest.nodes.filter { !it.synthetic }
    val syntheticNodes = manifest.nodes.filter { it.synthetic }
    val nodesByModule = realNodes.groupBy { it.moduleLabel }.toSortedMap()

    return createHTML(prettyPrint = false).div {
        unsafe { raw("<title>${escHtml(manifest.projectName)} UI Atlas</title>") }
        style { unsafe { raw(cssText) } }

        div("atlas-header") {
            div("atlas-title") { +"${manifest.projectName} UI Atlas" }
            div("atlas-stats") {
                span("stat") { +"${manifest.nodes.size} screens" }
                span("stat") { +"${manifest.nodes.sumOf { it.variants.size }} variants" }
                span("stat") { +"${manifest.edges.size} edges" }
                if (manifest.unresolvedEdges.isNotEmpty()) {
                    a(classes = "stat stat-warn") {
                        id = "unresolved-link"
                        href = "#"
                        +"${manifest.unresolvedEdges.size} unresolved"
                    }
                }
            }
            div("atlas-controls") {
                input(type = InputType.text, classes = "search-box") {
                    id = "search-input"
                    placeholder = "Search screens..."
                }
                button(classes = "ctrl-btn") { id = "zoom-fit-btn"; attributes["title"] ="Zoom to fit all"; +"Fit" }
                button(classes = "ctrl-btn") { id = "zoom-single-btn"; attributes["title"] ="Single-screen zoom level"; +"1:1" }
                button(classes = "ctrl-btn") { id = "zoom-in-btn"; attributes["title"] ="Zoom in"; +"+" }
                button(classes = "ctrl-btn") { id = "zoom-out-btn"; attributes["title"] ="Zoom out"; unsafe { raw("&minus;") } }
                button(classes = "ctrl-btn") { id = "export-csv-btn"; attributes["title"] ="Export annotations as CSV"; +"Export CSV" }
                button(classes = "ctrl-btn") {
                    id = "notes-btn"
                    attributes["title"] = "Browse annotations"
                    unsafe { raw("&#9998; ") }
                    span { id = "notes-count"; +"0" }
                }
            }
            div("atlas-timestamp") { +manifest.generatedAt }
        }

        div("search-results") {
            id = "search-results"
            attributes["hidden"] = "true"
        }

        div("viewport") {
            id = "viewport"
            div("world") {
                id = "world"

                for ((moduleLabel, moduleNodes) in nodesByModule) {
                    val featureGroups = moduleNodes.groupBy { it.featureGroup }.toSortedMap()
                    val moduleKey = moduleLabel.replace(":", "-")
                    div("module-group") {
                        div("module-header") {
                            attributes["data-collapse-key"] = "mod-$moduleKey"
                            span("chevron") { unsafe { raw("&#9662;") } }
                            span("module-name") { +moduleLabel }
                            span("group-count") { +"(${moduleNodes.size})" }
                        }
                        div("module-body collapsible-body") {
                            for ((feature, featureNodes) in featureGroups) {
                                val featureKey = "$moduleKey--$feature"
                                div("feature-group") {
                                    div("feature-header") {
                                        attributes["data-collapse-key"] = "feat-$featureKey"
                                        span("chevron") { unsafe { raw("&#9662;") } }
                                        span("feature-name") { +feature }
                                        span("group-count") { +"(${featureNodes.size})" }
                                    }
                                    div("feature-body collapsible-body") {
                                        for (node in featureNodes.sortedBy { it.displayName }) {
                                            renderScreenCard(
                                                node, htmlId(node.qualifiedName),
                                                dedup(incomingByDest[node.qualifiedName] ?: emptyList()),
                                                dedup(outgoingByDest[node.qualifiedName] ?: emptyList()),
                                                ::refThumbHtml, ::htmlId,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (syntheticNodes.isNotEmpty()) {
                    val syntheticByPkg = syntheticNodes.groupBy { it.featureGroup }.toSortedMap()
                    div("module-group") {
                        div("module-header collapsed") {
                            attributes["data-collapse-key"] = "mod-standalone"
                            span("chevron") { unsafe { raw("&#9662;") } }
                            span("module-name") { +"Standalone surfaces" }
                            span("group-count") { +"(${syntheticNodes.size})" }
                        }
                        div("module-body collapsible-body") {
                            for ((pkgGroup, pkgNodes) in syntheticByPkg) {
                                val featureKey = "standalone--${pkgGroup.replace('.', '-')}"
                                div("feature-group") {
                                    div("feature-header") {
                                        attributes["data-collapse-key"] = "feat-$featureKey"
                                        span("chevron") { unsafe { raw("&#9662;") } }
                                        span("feature-name") { +pkgGroup }
                                        span("group-count") { +"(${pkgNodes.size})" }
                                    }
                                    div("feature-body collapsible-body") {
                                        for (node in pkgNodes.sortedBy { it.displayName }) {
                                            renderSyntheticCard(node, htmlId(node.qualifiedName))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        div("annotation-panel") {
            id = "annotation-panel"
            attributes["hidden"] = "true"
            div("annotation-header") {
                span { id = "annotation-title"; +"Annotate" }
                button(classes = "ctrl-btn") { id = "annotation-close"; +"X" }
            }
            textArea(classes = "annotation-text") {
                id = "annotation-text"
                placeholder = "Add a note..."
            }
            div {
                attributes["style"] = "display:flex;gap:8px;"
                button(classes = "ctrl-btn") { id = "annotation-save"; +"Save" }
                button(classes = "ctrl-btn ctrl-btn-danger") { id = "annotation-delete"; +"Delete" }
            }
        }

        div("notes-browser") {
            id = "notes-browser"
            attributes["hidden"] = "true"
            div("notes-browser-header") {
                span("notes-browser-title") { +"Notes" }
                span("notes-pos") { id = "notes-pos" }
                button(classes = "ctrl-btn") { id = "notes-prev"; attributes["title"] ="Previous (p)"; unsafe { raw("&#9664;") } }
                button(classes = "ctrl-btn") { id = "notes-next"; attributes["title"] ="Next (n)"; unsafe { raw("&#9654;") } }
                button(classes = "ctrl-btn") { id = "notes-browser-close"; +"X" }
            }
            div("notes-list") { id = "notes-list" }
            div("notes-browser-footer") {
                button(classes = "ctrl-btn ctrl-btn-danger") { id = "annotation-clear-all"; +"Clear all notes" }
            }
        }

        details("footer-panel") {
            id = "appendix-panel"
            summary("footer-toggle") { +"Appendix (unresolved edges, unmatched goldens, zero-golden screens)" }
            div("footer-content") {
                if (manifest.unresolvedEdges.isNotEmpty()) {
                    h3 { +"Unresolved edges (${manifest.unresolvedEdges.size})" }
                    table("appendix-table") {
                        thead { tr { th { +"File" }; th { +"Line" }; th { +"Text" } } }
                        tbody {
                            for (ue in manifest.unresolvedEdges.take(100)) {
                                tr { td { +ue.file }; td { +"${ue.line}" }; td { +ue.text } }
                            }
                        }
                    }
                }
                val zeroGoldens = manifest.nodes.filter { it.variants.isEmpty() }
                if (zeroGoldens.isNotEmpty()) {
                    h3 { +"Screens with zero goldens (${zeroGoldens.size})" }
                    ul {
                        for (z in zeroGoldens) {
                            li { +"${z.screenName} (${z.featureGroup})" }
                        }
                    }
                }
                if (manifest.unmatchedGoldens > 0) {
                    p { +"${manifest.unmatchedGoldens} golden files did not match any screen." }
                }
            }
        }

        script {
            unsafe { raw("const MANIFEST = $manifestJson;\n$jsText") }
        }
    }.let { html ->
        // createHTML wraps in a root <div>; strip it to preserve the original unwrapped shape.
        html.removePrefix("<div>").removeSuffix("</div>")
    }
}

private fun kotlinx.html.DIV.renderScreenCard(
    node: AtlasNode,
    nid: String,
    incoming: List<EdgeRef>,
    outgoing: List<EdgeRef>,
    refThumbHtml: (String) -> String,
    htmlId: (String) -> String,
) {
    div("screen") {
        id = "card-$nid"
        attributes["data-dest"] = nid
        div("screen-caption") {
            span("screen-name") { +node.displayName }
            if (node.isShellActive) span("badge badge-shell") { +"shell" }
            if (node.isWithResult) span("badge badge-result") { +"WithResult" }
            if (node.navigationPath != null) span("screen-path") { +node.navigationPath }
        }
        div("screen-toolbar") {
            if (node.variants.size > 1) {
                select("variant-select") {
                    attributes["data-card-id"] = "card-$nid"
                    for ((vi, v) in node.variants.withIndex()) {
                        option {
                            value = "$vi"
                            if (vi == node.defaultVariantIndex) selected = true
                            +v.label
                        }
                    }
                }
            }
            button(classes = "annotate-btn") {
                attributes["data-dest"] = nid
                unsafe { raw("&#9998;") }
            }
            span("annotation-marker") {
                id = "marker-$nid"
                attributes["hidden"] = "true"
                unsafe { raw("&#9679;") }
            }
        }

        div("screen-row") {
            unsafe { raw("""<svg class="connectors" id="conn-${node.qualifiedName.hashCode().toUInt()}"></svg>""") }
            div("gutter gutter-in") {
                renderEdgeRefs(incoming, refThumbHtml, htmlId, inbound = true)
            }
            if (node.variants.isNotEmpty()) {
                val defaultVar = node.variants.getOrElse(node.defaultVariantIndex) { node.variants[0] }
                div("screen-img") {
                    img(classes = "snapshot") {
                        src = "images/${defaultVar.imagePath}"
                        attributes["data-variants"] = variantsJson(node)
                        alt = node.displayName
                        attributes["loading"] = "lazy"
                    }
                }
            } else {
                div("screen-img no-snapshot") { span { +"No snapshot" } }
            }
            div("gutter gutter-out") {
                renderEdgeRefs(outgoing, refThumbHtml, htmlId, inbound = false)
            }
        }
    }
}

private fun kotlinx.html.DIV.renderEdgeRefs(
    refs: List<EdgeRef>,
    refThumbHtml: (String) -> String,
    htmlId: (String) -> String,
    inbound: Boolean,
) {
    for (ref in refs) {
        val cls = when (ref.kind) { EdgeKind.CHROME -> " ref-chrome"; EdgeKind.RESULT -> " ref-result"; else -> "" }
        val kindAttr = when (ref.kind) { EdgeKind.CHROME -> "chrome"; EdgeKind.RESULT -> "result"; else -> "open" }
        val rid = htmlId(ref.dest)
        val thumb = refThumbHtml(ref.dest)
        val label = """<span class="ref-label">${escHtml(ref.display)}</span>"""
        val content = if (inbound) "$thumb$label" else "$label$thumb"
        unsafe {
            raw("""<a class="ref${escHtml(cls)}" data-dest="${escHtml(rid)}" data-kind="$kindAttr" title="${escHtml(ref.display)}">$content</a>""")
        }
    }
}

private fun kotlinx.html.DIV.renderSyntheticCard(node: AtlasNode, nid: String) {
    div("screen") {
        id = "card-$nid"
        attributes["data-dest"] = nid
        div("screen-caption") {
            span("screen-name") { +node.displayName }
            span("badge badge-standalone") { +"standalone" }
            span("screen-path") { +node.packageName }
        }
        div("screen-toolbar") {
            if (node.variants.size > 1) {
                select("variant-select") {
                    attributes["data-card-id"] = "card-$nid"
                    for ((vi, v) in node.variants.withIndex()) {
                        option {
                            value = "$vi"
                            if (vi == node.defaultVariantIndex) selected = true
                            +v.label
                        }
                    }
                }
            }
            button(classes = "annotate-btn") {
                attributes["data-dest"] = nid
                unsafe { raw("&#9998;") }
            }
            span("annotation-marker") {
                id = "marker-$nid"
                attributes["hidden"] = "true"
                unsafe { raw("&#9679;") }
            }
        }
        if (node.variants.isNotEmpty()) {
            val defaultVar = node.variants.getOrElse(node.defaultVariantIndex) { node.variants[0] }
            div("screen-img") {
                img(classes = "snapshot") {
                    src = "images/${defaultVar.imagePath}"
                    attributes["data-variants"] = variantsJson(node)
                    alt = node.displayName
                    attributes["loading"] = "lazy"
                }
            }
        } else {
            div("screen-img no-snapshot") { span { +"No snapshot" } }
        }
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

private fun escHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

fun writeHtml(config: ResolvedAtlasConfig, manifest: AtlasManifest) {
    config.outputDir.mkdirs()
    File(config.outputDir, "index.html").writeText(generateHtml(config, manifest))
}
