package dev.isaacudy.udytils.htmx

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.html.p
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmxCallTest {

    @Test
    fun `fragments answer htmx requests, pages answer the rest`() = testApplication {
        routing {
            get("/greetings") {
                call.varyOnHtmx()
                if (call.isHtmx) {
                    call.hxTrigger("greetings-loaded")
                    call.respondHtmlFragment(HttpStatusCode.UnprocessableEntity) { p { +"fragment" } }
                } else {
                    call.respondText("page")
                }
            }
        }

        val fragment = client.get("/greetings") { header("HX-Request", "true") }
        assertEquals(HttpStatusCode.UnprocessableEntity, fragment.status)
        assertEquals("<p>fragment</p>", fragment.bodyAsText())
        assertTrue(fragment.contentType()!!.match(ContentType.Text.Html))
        assertEquals("greetings-loaded", fragment.headers["HX-Trigger"])
        assertEquals("HX-Request", fragment.headers[HttpHeaders.Vary])

        assertEquals("page", client.get("/greetings").bodyAsText())
    }

    @Test
    fun `bundled scripts are served with an immutable cache policy`() = testApplication {
        routing { htmxAssets() }

        listOf(HtmxAssets.HTMX, HtmxAssets.SSE_EXTENSION, HtmxAssets.ALPINE_CSP).forEach { asset ->
            val response = client.get("${HtmxAssets.DEFAULT_PATH}/$asset")
            assertEquals(HttpStatusCode.OK, response.status, asset)
            assertTrue("max-age=31536000" in response.headers[HttpHeaders.CacheControl].orEmpty(), asset)
        }
    }
}
