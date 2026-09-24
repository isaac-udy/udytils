package dev.isaacudy.udytils.htmlsnapshot

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Rewrites parts of a parsed document that change between renders, such as generated tokens. */
fun interface HtmlScrubber {
    fun scrub(root: Element)

    companion object {
        /** Replaces the value of every [attribute] with [replacement]. */
        fun attribute(attribute: String, replacement: String = "[scrubbed]"): HtmlScrubber = HtmlScrubber { root ->
            root.select("[$attribute]").forEach { it.attr(attribute, replacement) }
        }
    }
}

/**
 * Parses [html] and prints it with one element per line and attributes in name order, so two
 * renders compare equal exactly when their structure, attributes and text are equal. A document
 * (starting with a doctype or `<html>`) is printed whole; anything else is printed as a fragment.
 */
fun normalizeHtml(html: String, scrubbers: List<HtmlScrubber> = emptyList()): String {
    val start = html.trimStart()
    val isDocument = start.startsWith("<!doctype", ignoreCase = true) || start.startsWith("<html", ignoreCase = true)
    val document = if (isDocument) Jsoup.parse(html) else Jsoup.parseBodyFragment(html)
    val root: Element = if (isDocument) document else document.body()

    scrubbers.forEach { it.scrub(root) }
    root.allElements.forEach(::sortAttributes)
    document.outputSettings().prettyPrint(true).outline(true).indentAmount(2)

    val printed = if (isDocument) document.outerHtml() else document.body().html()
    return printed.trim() + "\n"
}

private fun sortAttributes(element: Element) {
    val sorted = element.attributes().asList().sortedBy { it.key }
    sorted.forEach { element.removeAttr(it.key) }
    sorted.forEach { element.attr(it.key, it.value) }
}
