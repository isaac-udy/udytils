package dev.isaacudy.udytils.htmx

import kotlinx.html.HTMLTag

/** An element id shared by the markup that renders an element and the attributes that target it. */
@JvmInline
value class ElementId(val value: String) {
    init {
        require(pattern.matches(value)) {
            "Element id `$value` must start with a letter and contain only letters, digits, '-' and '_'"
        }
    }

    val selector: String get() = "#$value"

    override fun toString(): String = value

    private companion object {
        val pattern = Regex("[A-Za-z][A-Za-z0-9_-]*")
    }
}

var HTMLTag.elementId: ElementId
    get() = ElementId(checkNotNull(attributes["id"]) { "<$tagName> has no id" })
    set(value) {
        attributes["id"] = value.value
    }
