package dev.isaacudy.udytils.htmx

import kotlinx.html.button
import kotlinx.html.div
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class AttributesTest {

    private val list = ElementId("greetings")

    @Test
    fun `hx attributes render their htmx values`() {
        val button = single {
            button {
                hx {
                    post("/greetings")
                    target(list)
                    swap(Swap(SwapStyle.BeforeEnd, transition = true, settleDelay = 20.milliseconds))
                    trigger(Trigger.on("keyup").changed().delay(500.milliseconds), Trigger.Load)
                    disabledElement(HxTarget.This)
                    include(HxTarget.closest("form"))
                    vals(mapOf("source" to "button \"a\""))
                    pushUrl()
                }
            }
        }

        assertEquals("/greetings", button.attr("hx-post"))
        assertEquals("#greetings", button.attr("hx-target"))
        assertEquals("beforeend transition:true settle:20ms", button.attr("hx-swap"))
        assertEquals("keyup changed delay:500ms, load", button.attr("hx-trigger"))
        assertEquals("this", button.attr("hx-disabled-elt"))
        assertEquals("closest form", button.attr("hx-include"))
        assertEquals("""{"source":"button \"a\""}""", button.attr("hx-vals"))
        assertEquals("true", button.attr("hx-push-url"))
    }

    @Test
    fun `swapOob spells true, a style, or a style aimed at a target`() {
        assertEquals("true", single { div { hx { swapOob() } } }.attr("hx-swap-oob"))
        assertEquals("innerHTML", single { div { hx { swapOob(Swap(SwapStyle.InnerHtml)) } } }.attr("hx-swap-oob"))
        assertEquals(
            "beforeend:#greetings",
            single { div { hx { swapOob(Swap(SwapStyle.BeforeEnd), list) } } }.attr("hx-swap-oob"),
        )
    }

    @Test
    fun `sse connect adds the extension once`() {
        val element = single {
            div {
                sse {
                    connect("/greetings/events")
                    connect("/greetings/events")
                    swap("update")
                }
            }
        }

        assertEquals("sse", element.attr("hx-ext"))
        assertEquals("/greetings/events", element.attr("sse-connect"))
        assertEquals("update", element.attr("sse-swap"))
    }

    @Test
    fun `alpine directives name members`() {
        val element = single {
            div {
                alpine {
                    data("counter")
                    on("click.outside", "close")
                    bind("class", "classes.active")
                }
            }
        }

        assertEquals("counter", element.attr("x-data"))
        assertEquals("close", element.attr("x-on:click.outside"))
        assertEquals("classes.active", element.attr("x-bind:class"))
    }

    @Test
    fun `alpine directives reject expressions`() {
        assertFailsWith<IllegalArgumentException> {
            renderHtmlFragment { div { alpine { on("click", "open = !open") } } }
        }
    }

    @Test
    fun `triggers reject event filters`() {
        assertFailsWith<IllegalArgumentException> { Trigger.on("click[ctrlKey]") }
    }

    @Test
    fun `element ids reject selectors`() {
        assertFailsWith<IllegalArgumentException> { ElementId("#greetings") }
    }

    private fun single(block: kotlinx.html.FlowContent.() -> Unit): Element =
        Jsoup.parseBodyFragment(renderHtmlFragment(block)).body().child(0)
}
