package dev.isaacudy.udytils.htmx

import kotlinx.html.HTMLTag
import kotlinx.html.HtmlTagMarker

/** Attributes of the htmx `sse` extension. */
@HtmlTagMarker
class Sse internal constructor(private val attributes: MutableMap<String, String>) {

    /** Opens an event stream from [url] and enables the extension on this element. */
    fun connect(url: String) {
        attributes["sse-connect"] = url
        val extensions = attributes["hx-ext"]?.split(',')?.map(String::trim).orEmpty()
        if ("sse" !in extensions) attributes["hx-ext"] = (extensions + "sse").joinToString(",")
    }

    /** Swaps this element with the data of each named event, using its `hx-swap` style. */
    fun swap(vararg events: String) {
        require(events.isNotEmpty()) { "sse-swap needs at least one event name" }
        attributes["sse-swap"] = events.joinToString(",") { Trigger.on(it).value }
    }

    fun close(event: String) {
        attributes["sse-close"] = Trigger.on(event).value
    }
}

fun HTMLTag.sse(block: Sse.() -> Unit) {
    Sse(attributes).block()
}
