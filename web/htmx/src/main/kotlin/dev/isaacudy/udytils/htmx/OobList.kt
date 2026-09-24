package dev.isaacudy.udytils.htmx

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.html.DIV
import kotlinx.html.TBODY
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.UL
import kotlinx.html.stream.createHTML

/**
 * Keeps a rendered list in step with a changing `List<T>` through out-of-band swaps.
 *
 * [renderItem] renders one item as exactly one element carrying an `id`, with the same receiver
 * the page uses inside the [container] element; [parent] creates that receiver. Items are matched
 * between lists by [key] and re-rendered when [sameContent] is false. Each swap is wrapped in its
 * own `<template>`, so table rows and list items parse in any combination.
 */
class OobList<P : Tag, T, K>(
    val container: ElementId,
    private val parent: (TagConsumer<*>) -> P,
    private val key: (T) -> K,
    private val sameContent: (T, T) -> Boolean = { a, b -> a == b },
    private val renderItem: P.(T) -> Unit,
) {
    private val parentTagName = parent(createHTML()).tagName

    /** Replaces the container's children with [items]. */
    fun snapshot(items: List<T>): String = template(
        """<$parentTagName id="${container.value}" hx-swap-oob="${SwapStyle.InnerHtml.value}">""" +
            items.joinToString("") { render(it, oob = null) } + "</$parentTagName>",
    )

    /**
     * The swaps that turn [previous] into [current], or null when nothing changed. A change that
     * reorders items or inserts one before an existing item falls back to [snapshot].
     */
    fun changes(previous: List<T>, current: List<T>): String? {
        val previousByKey = previous.associateByUniqueKey()
        val currentByKey = current.associateByUniqueKey()

        val retainedInPreviousOrder = previous.map(key).filter { it in currentByKey }
        val retainedInCurrentOrder = current.map(key).filter { it in previousByKey }
        val firstAdded = current.indexOfFirst { key(it) !in previousByKey }
        val insertsBeforeExisting = firstAdded >= 0 && current.drop(firstAdded).any { key(it) in previousByKey }
        if (retainedInPreviousOrder != retainedInCurrentOrder || insertsBeforeExisting) return snapshot(current)

        val removed = previous.filter { key(it) !in currentByKey }
        val updated = current.filter { item ->
            previousByKey[key(item)]?.let { !sameContent(it, item) } ?: false
        }
        val added = current.filter { key(it) !in previousByKey }
        if (removed.isEmpty() && updated.isEmpty() && added.isEmpty()) return null

        return buildString {
            removed.forEach { append(template(render(it, oob = SwapStyle.Delete.value))) }
            updated.forEach { append(template(render(it, oob = "true"))) }
            if (added.isNotEmpty()) {
                append(
                    template(
                        """<$parentTagName hx-swap-oob="${SwapStyle.BeforeEnd.value}:${container.selector}">""" +
                            added.joinToString("") { render(it, oob = null) } + "</$parentTagName>",
                    ),
                )
            }
        }
    }

    private fun List<T>.associateByUniqueKey(): Map<K, T> {
        val byKey = associateBy(key)
        require(byKey.size == size) { "OobList keys must be unique within a list" }
        return byKey
    }

    private fun render(item: T, oob: String?): String {
        val consumer = RootAttributeConsumer(createHTML(prettyPrint = false), oob)
        // The parent tag is never visited, so only the item's own tags reach the consumer.
        parent(consumer).renderItem(item)
        check(consumer.roots == 1) { "OobList items must render exactly one root element, found ${consumer.roots}" }
        checkNotNull(consumer.rootId) { "OobList items must render a root element with an id" }
        return consumer.finalize()
    }

    companion object {
        fun <T, K> tableBody(
            container: ElementId,
            key: (T) -> K,
            sameContent: (T, T) -> Boolean = { a, b -> a == b },
            renderItem: TBODY.(T) -> Unit,
        ): OobList<TBODY, T, K> = OobList(container, { TBODY(emptyMap(), it) }, key, sameContent, renderItem)

        fun <T, K> unorderedList(
            container: ElementId,
            key: (T) -> K,
            sameContent: (T, T) -> Boolean = { a, b -> a == b },
            renderItem: UL.(T) -> Unit,
        ): OobList<UL, T, K> = OobList(container, { UL(emptyMap(), it) }, key, sameContent, renderItem)

        fun <T, K> div(
            container: ElementId,
            key: (T) -> K,
            sameContent: (T, T) -> Boolean = { a, b -> a == b },
            renderItem: DIV.(T) -> Unit,
        ): OobList<DIV, T, K> = OobList(container, { DIV(emptyMap(), it) }, key, sameContent, renderItem)

        private fun template(content: String): String = "<template>$content</template>"
    }
}

/** A full [OobList.snapshot] for the first list, then [OobList.changes] for each later one. */
fun <P : Tag, T, K> Flow<List<T>>.oobUpdates(list: OobList<P, T, K>): Flow<String> = flow {
    var previous: List<T>? = null
    collect { current ->
        val last = previous
        val update = if (last == null) list.snapshot(current) else list.changes(last, current)
        previous = current
        if (update != null) emit(update)
    }
}

/**
 * Adds `hx-swap-oob` to each root element as it starts. Attributes a builder sets later still
 * arrive, because the downstream consumer writes a start tag only once its content begins.
 */
private class RootAttributeConsumer<R>(
    private val downstream: TagConsumer<R>,
    private val oob: String?,
) : TagConsumer<R> by downstream {
    private var depth = 0
    private var injecting = false
    var roots = 0
        private set
    var rootId: String? = null
        private set

    override fun onTagStart(tag: Tag) {
        if (depth == 0) {
            roots++
            if (oob != null) {
                injecting = true
                tag.attributes["hx-swap-oob"] = oob
                injecting = false
            }
        }
        depth++
        downstream.onTagStart(tag)
    }

    override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
        if (!injecting) downstream.onTagAttributeChange(tag, attribute, value)
    }

    override fun onTagEnd(tag: Tag) {
        depth--
        if (depth == 0) rootId = tag.attributes["id"]
        downstream.onTagEnd(tag)
    }
}
