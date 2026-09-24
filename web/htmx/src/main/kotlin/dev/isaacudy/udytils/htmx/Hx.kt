package dev.isaacudy.udytils.htmx

import kotlinx.html.HTMLTag
import kotlinx.html.HtmlTagMarker
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Typed `hx-*` attributes. `hx-on:*` is deliberately absent: its value is inline JavaScript, which
 * belongs in a script file.
 */
@HtmlTagMarker
class Hx internal constructor(private val attributes: MutableMap<String, String>) {

    fun get(url: String) {
        attributes["hx-get"] = url
    }

    fun post(url: String) {
        attributes["hx-post"] = url
    }

    fun put(url: String) {
        attributes["hx-put"] = url
    }

    fun patch(url: String) {
        attributes["hx-patch"] = url
    }

    fun delete(url: String) {
        attributes["hx-delete"] = url
    }

    fun target(id: ElementId) = target(HxTarget.element(id))

    fun target(target: HxTarget) {
        attributes["hx-target"] = target.value
    }

    fun swap(style: SwapStyle) = swap(Swap(style))

    fun swap(swap: Swap) {
        attributes["hx-swap"] = swap.value
    }

    /** `hx-swap-oob`: `true` when [swap] is null, otherwise the swap, aimed at [target] when given. */
    fun swapOob(swap: Swap? = null, target: ElementId? = null) {
        require(swap != null || target == null) { "An out-of-band target needs a swap style" }
        attributes["hx-swap-oob"] = when {
            swap == null -> "true"
            target == null -> swap.value
            else -> "${swap.value}:${target.selector}"
        }
    }

    fun trigger(vararg triggers: Trigger) {
        require(triggers.isNotEmpty()) { "hx-trigger needs at least one trigger" }
        attributes["hx-trigger"] = triggers.joinToString(", ") { it.value }
    }

    fun select(selector: String) {
        attributes["hx-select"] = selector
    }

    /** `hx-push-url`: the request URL when [url] is null. */
    fun pushUrl(url: String? = null) {
        attributes["hx-push-url"] = url ?: "true"
    }

    /** `hx-replace-url`: the request URL when [url] is null. */
    fun replaceUrl(url: String? = null) {
        attributes["hx-replace-url"] = url ?: "true"
    }

    fun indicator(id: ElementId) {
        attributes["hx-indicator"] = id.selector
    }

    fun disabledElement(target: HxTarget) {
        attributes["hx-disabled-elt"] = target.value
    }

    fun include(target: HxTarget) {
        attributes["hx-include"] = target.value
    }

    fun vals(values: Map<String, String>) {
        attributes["hx-vals"] = values.toJson()
    }

    fun headers(values: Map<String, String>) {
        attributes["hx-headers"] = values.toJson()
    }

    fun confirm(message: String) {
        attributes["hx-confirm"] = message
    }

    /** `hx-ext`: enables htmx [extensions] on this element and its descendants, keeping any already enabled. */
    fun ext(vararg extensions: String) {
        require(extensions.isNotEmpty()) { "hx-ext needs at least one extension name" }
        extensions.forEach { require(extensionName.matches(it)) { "`$it` is not an htmx extension name" } }
        val enabled = attributes["hx-ext"]?.split(',')?.map(String::trim).orEmpty()
        attributes["hx-ext"] = (enabled + extensions.filterNot { it in enabled }).distinct().joinToString(",")
    }

    fun boost(enabled: Boolean = true) {
        attributes["hx-boost"] = enabled.toString()
    }

    fun preserve() {
        attributes["hx-preserve"] = "true"
    }

    private fun Map<String, String>.toJson(): String =
        JsonObject(mapValues { JsonPrimitive(it.value) }).toString()
}

fun HTMLTag.hx(block: Hx.() -> Unit) {
    Hx(attributes).block()
}

private val extensionName = Regex("[a-z][a-z0-9-]*")
