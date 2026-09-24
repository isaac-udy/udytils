package dev.isaacudy.udytils.htmx

import io.ktor.http.CacheControl
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route
import kotlinx.html.HEAD
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** The htmx, htmx `sse` extension and Alpine CSP builds bundled with this library. */
object HtmxAssets {
    const val DEFAULT_PATH: String = "/assets/htmx"
    const val HTMX: String = "htmx-2.0.11.min.js"
    const val SSE_EXTENSION: String = "htmx-ext-sse-2.2.4.min.js"
    const val ALPINE_CSP: String = "alpine-csp-3.17.4.min.js"

    internal const val RESOURCE_PACKAGE: String = "dev/isaacudy/udytils/htmx/assets"
}

/** Serves [HtmxAssets] under [path]. File names carry their version, so they are cached as immutable. */
fun Route.htmxAssets(path: String = HtmxAssets.DEFAULT_PATH) {
    staticResources(path, HtmxAssets.RESOURCE_PACKAGE) {
        cacheControl { listOf(CacheControl.MaxAge(maxAgeSeconds = 31_536_000, visibility = CacheControl.Visibility.Public)) }
    }
}

/**
 * The `htmx-config` meta tag. The defaults keep htmx inside a `script-src 'self'` content security
 * policy: no `eval`, no inline indicator `<style>`, and no scripts executed from swapped content.
 * With [swapUnprocessableContent], a `422` response is swapped like a `200`, so a form can be
 * re-rendered with its errors under the status that says it was rejected; every other status
 * keeps htmx's default handling.
 */
fun HEAD.htmxConfig(
    allowEval: Boolean = false,
    allowScriptTags: Boolean = false,
    includeIndicatorStyles: Boolean = false,
    selfRequestsOnly: Boolean = true,
    swapUnprocessableContent: Boolean = true,
) {
    val config = buildJsonObject {
        put("allowEval", JsonPrimitive(allowEval))
        put("allowScriptTags", JsonPrimitive(allowScriptTags))
        put("includeIndicatorStyles", JsonPrimitive(includeIndicatorStyles))
        put("selfRequestsOnly", JsonPrimitive(selfRequestsOnly))
        if (swapUnprocessableContent) {
            put(
                "responseHandling",
                buildJsonArray {
                    add(responseHandling(code = "204", swap = false))
                    add(responseHandling(code = "[23]..", swap = true))
                    add(responseHandling(code = "422", swap = true))
                    add(responseHandling(code = "[45]..", swap = false, error = true))
                    add(responseHandling(code = "...", swap = false))
                },
            )
        }
    }
    meta(name = "htmx-config", content = config.toString())
}

private fun responseHandling(code: String, swap: Boolean, error: Boolean = false) = buildJsonObject {
    put("code", JsonPrimitive(code))
    put("swap", JsonPrimitive(swap))
    if (error) put("error", JsonPrimitive(true))
}

fun HEAD.htmxScripts(path: String = HtmxAssets.DEFAULT_PATH, sse: Boolean = true) {
    deferredScript("$path/${HtmxAssets.HTMX}")
    if (sse) deferredScript("$path/${HtmxAssets.SSE_EXTENSION}")
}

/**
 * The Alpine CSP build. Deferred scripts run in document order, so scripts that register
 * components on `alpine:init` must be placed before this one.
 */
fun HEAD.alpineScript(path: String = HtmxAssets.DEFAULT_PATH) {
    deferredScript("$path/${HtmxAssets.ALPINE_CSP}")
}

private fun HEAD.deferredScript(src: String) {
    script(src = src) { defer = true }
}
