package dev.isaacudy.udytils.htmx

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.TagConsumer
import kotlinx.html.stream.createHTML

/** Renders [block] as a sequence of top-level elements, with no enclosing document or wrapper. */
fun renderHtmlFragment(block: FlowContent.() -> Unit): String = renderInto(createHTML(prettyPrint = false), block)

suspend fun ApplicationCall.respondHtmlFragment(
    status: HttpStatusCode = HttpStatusCode.OK,
    block: FlowContent.() -> Unit,
) {
    respondText(renderHtmlFragment(block), ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
}

// The DIV is never visited, so only the tags built inside it reach the consumer.
internal fun <R> renderInto(consumer: TagConsumer<R>, block: FlowContent.() -> Unit): R {
    DIV(emptyMap(), consumer).block()
    return consumer.finalize()
}
