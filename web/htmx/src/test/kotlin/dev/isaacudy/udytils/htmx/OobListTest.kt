package dev.isaacudy.udytils.htmx

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.html.TBODY
import kotlinx.html.li
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.tr
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OobListTest {

    private data class Greeting(val id: Int, val text: String)

    private val rows = OobList.tableBody<Greeting, Int>(ElementId("rows"), key = { it.id }) { greetingRow(it) }

    private val a = Greeting(1, "a")
    private val b = Greeting(2, "b")
    private val c = Greeting(3, "c")

    @Test
    fun `snapshot replaces the container children inside a template`() {
        assertEquals(
            """<template><tbody id="rows" hx-swap-oob="innerHTML"><tr id="greeting-1"><td>a</td></tr><tr id="greeting-2"><td>b</td></tr></tbody></template>""",
            rows.snapshot(listOf(a, b)),
        )
    }

    @Test
    fun `changes delete, update and append in separate templates`() {
        val changes = rows.changes(listOf(a, b), listOf(b.copy(text = "b2"), c))

        assertEquals(
            """<template><tr hx-swap-oob="delete" id="greeting-1"><td>a</td></tr></template>""" +
                """<template><tr hx-swap-oob="true" id="greeting-2"><td>b2</td></tr></template>""" +
                """<template><tbody hx-swap-oob="beforeend:#rows"><tr id="greeting-3"><td>c</td></tr></tbody></template>""",
            changes,
        )
    }

    @Test
    fun `every template parses to the element htmx swaps`() {
        val changes = checkNotNull(rows.changes(listOf(a, b), listOf(b.copy(text = "b2"), c)))
        val templates = Jsoup.parseBodyFragment(changes).select("template")

        assertEquals(listOf("tr", "tr", "tbody"), templates.map { it.child(0).tagName() })
        assertEquals("tr", templates[2].child(0).child(0).tagName())
    }

    @Test
    fun `unchanged lists produce nothing`() {
        assertNull(rows.changes(listOf(a, b), listOf(a, b)))
    }

    @Test
    fun `reordering or inserting before an existing item falls back to a snapshot`() {
        assertEquals(rows.snapshot(listOf(b, a)), rows.changes(listOf(a, b), listOf(b, a)))
        assertEquals(rows.snapshot(listOf(c, a)), rows.changes(listOf(a), listOf(c, a)))
    }

    @Test
    fun `list items append through their parent tag`() {
        val items = OobList.unorderedList<Greeting, Int>(ElementId("items"), key = { it.id }) { greeting ->
            li { elementId = ElementId("item-${greeting.id}"); +greeting.text }
        }

        assertEquals(
            """<template><ul hx-swap-oob="beforeend:#items"><li id="item-2">b</li></ul></template>""",
            items.changes(listOf(a), listOf(a, b)),
        )
    }

    @Test
    fun `items must render one root element with an id`() {
        val withoutId = OobList.unorderedList<Greeting, Int>(ElementId("items"), key = { it.id }) { li { +it.text } }
        val twoRoots = OobList.unorderedList<Greeting, Int>(ElementId("items"), key = { it.id }) { greeting ->
            li { elementId = ElementId("x-${greeting.id}") }
            li { elementId = ElementId("y-${greeting.id}") }
        }

        assertFailsWith<IllegalStateException> { withoutId.snapshot(listOf(a)) }
        assertFailsWith<IllegalStateException> { twoRoots.snapshot(listOf(a)) }
    }

    @Test
    fun `keys must be unique`() {
        assertFailsWith<IllegalArgumentException> { rows.changes(listOf(a), listOf(a, a.copy(text = "again"))) }
    }

    @Test
    fun `oobUpdates emits a snapshot, then only the changes`() = runTest {
        val updates = flowOf(listOf(a), listOf(a), listOf(a, b)).oobUpdates(rows).toList()

        assertEquals(listOf(rows.snapshot(listOf(a)), rows.changes(listOf(a), listOf(a, b))), updates)
    }

    @Test
    fun `the page renders rows with the same receiver`() {
        val page = renderHtmlFragment {
            table { tbody { elementId = rows.container; listOf(a, b).forEach { greetingRow(it) } } }
        }

        assertEquals(
            """<table><tbody id="rows"><tr id="greeting-1"><td>a</td></tr><tr id="greeting-2"><td>b</td></tr></tbody></table>""",
            page,
        )
    }

    private fun TBODY.greetingRow(greeting: Greeting) {
        tr {
            elementId = ElementId("greeting-${greeting.id}")
            td { +greeting.text }
        }
    }
}
