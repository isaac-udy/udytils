package dev.isaacudy.udytils.htmx

import kotlin.time.Duration

enum class SwapStyle(val value: String) {
    InnerHtml("innerHTML"),
    OuterHtml("outerHTML"),
    TextContent("textContent"),
    BeforeBegin("beforebegin"),
    AfterBegin("afterbegin"),
    BeforeEnd("beforeend"),
    AfterEnd("afterend"),
    Delete("delete"),
    None("none"),
}

enum class ScrollTo(val value: String) {
    Top("top"),
    Bottom("bottom"),
}

/** An `hx-swap` value: a [style] plus the modifiers htmx accepts after it. */
data class Swap(
    val style: SwapStyle,
    val transition: Boolean = false,
    val swapDelay: Duration? = null,
    val settleDelay: Duration? = null,
    val scroll: ScrollTo? = null,
    val show: ScrollTo? = null,
    val ignoreTitle: Boolean = false,
) {
    val value: String
        get() = buildList {
            add(style.value)
            if (transition) add("transition:true")
            swapDelay?.let { add("swap:${it.htmx}") }
            settleDelay?.let { add("settle:${it.htmx}") }
            scroll?.let { add("scroll:${it.value}") }
            show?.let { add("show:${it.value}") }
            if (ignoreTitle) add("ignoreTitle:true")
        }.joinToString(" ")

    override fun toString(): String = value
}

internal val Duration.htmx: String get() = "${inWholeMilliseconds}ms"
