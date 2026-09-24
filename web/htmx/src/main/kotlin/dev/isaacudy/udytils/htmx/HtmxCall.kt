package dev.isaacudy.udytils.htmx

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

/** True for requests htmx issued, whose response is swapped into the page rather than loaded. */
val ApplicationCall.isHtmx: Boolean get() = request.headers["HX-Request"] == "true"

val ApplicationCall.isBoosted: Boolean get() = request.headers["HX-Boosted"] == "true"

/** Marks a response whose body depends on [isHtmx], so caches keep the page and fragment apart. */
fun ApplicationCall.varyOnHtmx() {
    response.headers.append(HttpHeaders.Vary, "HX-Request")
}

/** A full-page navigation to [url]. */
fun ApplicationCall.hxRedirect(url: String) {
    response.headers.append("HX-Redirect", url)
}

/** A client-side navigation to [url] that swaps the body without a full page load. */
fun ApplicationCall.hxLocation(url: String) {
    response.headers.append("HX-Location", url)
}

fun ApplicationCall.hxPushUrl(url: String) {
    response.headers.append("HX-Push-Url", url)
}

fun ApplicationCall.hxReplaceUrl(url: String) {
    response.headers.append("HX-Replace-Url", url)
}

fun ApplicationCall.hxRefresh() {
    response.headers.append("HX-Refresh", "true")
}

fun ApplicationCall.hxTrigger(vararg events: String) {
    require(events.isNotEmpty()) { "HX-Trigger needs at least one event name" }
    response.headers.append("HX-Trigger", events.joinToString(", ") { Trigger.on(it).value })
}

fun ApplicationCall.hxRetarget(target: HxTarget) {
    response.headers.append("HX-Retarget", target.value)
}

fun ApplicationCall.hxReswap(swap: Swap) {
    response.headers.append("HX-Reswap", swap.value)
}
