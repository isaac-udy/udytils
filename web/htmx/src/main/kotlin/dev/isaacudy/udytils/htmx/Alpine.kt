package dev.isaacudy.udytils.htmx

import kotlinx.html.HTMLTag
import kotlinx.html.HtmlTagMarker

/**
 * Alpine.js directives for the CSP build. Every value names a component registered with
 * `Alpine.data(...)` in a script file, or a property or method on it; expressions are rejected.
 */
@HtmlTagMarker
class Alpine internal constructor(private val attributes: MutableMap<String, String>) {

    fun data(component: String) {
        attributes["x-data"] = component.requireName()
    }

    fun on(event: String, method: String) {
        require(eventWithModifiers.matches(event)) { "`$event` is not an event name with modifiers" }
        attributes["x-on:$event"] = method.requireName()
    }

    fun text(property: String) {
        attributes["x-text"] = property.requireName()
    }

    fun show(property: String) {
        attributes["x-show"] = property.requireName()
    }

    fun bind(attribute: String, property: String) {
        require(attributeName.matches(attribute)) { "`$attribute` is not an attribute name" }
        attributes["x-bind:$attribute"] = property.requireName()
    }

    fun model(property: String) {
        attributes["x-model"] = property.requireName()
    }

    fun ref(name: String) {
        attributes["x-ref"] = name.requireName()
    }

    fun init(method: String) {
        attributes["x-init"] = method.requireName()
    }

    fun cloak() {
        attributes["x-cloak"] = ""
    }

    private fun String.requireName(): String = also {
        require(propertyPath.matches(it)) { "`$it` must name a property or method, not an expression" }
    }

    private companion object {
        val propertyPath = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
        val eventWithModifiers = Regex("[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)?(\\.[a-z0-9-]+)*")
        val attributeName = Regex("[a-z][a-z0-9-]*")
    }
}

fun HTMLTag.alpine(block: Alpine.() -> Unit) {
    Alpine(attributes).block()
}
