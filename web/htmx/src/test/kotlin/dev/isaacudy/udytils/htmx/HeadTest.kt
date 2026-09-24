package dev.isaacudy.udytils.htmx

import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadTest {

    private val document = Jsoup.parse(
        createHTML().html {
            head {
                htmxConfig()
                htmxScripts()
                alpineScript()
            }
        },
    )

    @Test
    fun `the config forbids eval and swaps 422 responses`() {
        val config = Json.parseToJsonElement(document.select("meta[name=htmx-config]").attr("content")).jsonObject

        assertEquals("false", config.getValue("allowEval").jsonPrimitive.content)
        val swapped = (config.getValue("responseHandling") as JsonArray)
            .map { it.jsonObject }
            .filter { it.getValue("swap").jsonPrimitive.content == "true" }
            .map { it.getValue("code").jsonPrimitive.content }
        assertEquals(listOf("[23]..", "422"), swapped)
    }

    @Test
    fun `scripts are deferred in order, alpine last`() {
        val scripts = document.select("script")

        assertEquals(
            listOf(HtmxAssets.HTMX, HtmxAssets.SSE_EXTENSION, HtmxAssets.ALPINE_CSP),
            scripts.map { it.attr("src").substringAfterLast('/') },
        )
        assertEquals(listOf(true, true, true), scripts.map { it.hasAttr("defer") })
    }
}
