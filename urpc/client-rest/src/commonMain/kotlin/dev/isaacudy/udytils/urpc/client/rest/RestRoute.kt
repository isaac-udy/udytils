package dev.isaacudy.udytils.urpc.client.rest

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * One entry in a REST route table: how a single urpc function is served over HTTP.
 *
 * Opaque by design — instances come only from the builders inside a
 * [service][RestUrpcConfig.service] block ([RestServiceConfig.get], [RestServiceConfig.post],
 * [RestServiceConfig.custom], …) and are bound to a wire name with
 * [via][RestServiceConfig.via]. Keeping the shape private means new route kinds can be added
 * without breaking source or binary compatibility for callers.
 */
sealed class RestRoute {

    /** A mapped path + method, with the request marshalled into the URL and/or a JSON body. */
    internal class Http internal constructor(
        val method: HttpMethod,
        val template: RestPathTemplate,
        val sendsBody: Boolean,
        val responseTransform: ((JsonElement) -> JsonElement)?,
    ) : RestRoute() {
        override fun toString(): String = "${method.value} ${template.raw}"
    }

    /** A hand-written handler; none of the path/query/body marshalling applies to it. */
    internal class Custom internal constructor(
        val handler: suspend RestCallScope.(Any?) -> Any?,
    ) : RestRoute() {
        override fun toString(): String = "custom { }"
    }
}

/**
 * Receiver of a [custom][RestServiceConfig.custom] route handler: everything the REST client
 * knows about the call, so the handler can make whatever request the endpoint actually needs.
 *
 * A custom handler owns the whole exchange. The interceptor chain has already run (its metadata
 * is in [headers], merged with the service's static headers), but nothing else the route table
 * does applies: there is no automatic 401 refresh-and-retry and no automatic error decoding.
 * Reuse [errorDecoder] to keep failures indistinguishable from the mapped routes:
 *
 * ```
 * "exportReport" via custom<ExportRequest, ExportResponse> { request ->
 *     val response = httpClient.submitForm(
 *         url = "$baseUrl/reports/export",
 *         formParameters = parameters { append("range", request.range) },
 *     ) { applyCallHeaders() }
 *     if (!response.status.isSuccess()) throw errorDecoder.decode(response.status.value, response.bodyAsText())
 *     ExportResponse(url = response.bodyAsText())
 * }
 * ```
 */
class RestCallScope internal constructor(
    /** The client the route table was built on — use it to issue the request. */
    val httpClient: HttpClient,
    /** The `baseUrl` the route table was built with, verbatim. */
    val baseUrl: String,
    /** The route table's [Json], so hand-rolled encoding matches the mapped routes. */
    val json: Json,
    /** The urpc wire name being served, e.g. `"userService.exportReport"`. */
    val wireName: String,
    /** Service-level static headers merged with the interceptor chain's metadata. */
    val headers: Map<String, String>,
    /** The route table's error decoder, so failures surface the same way as on mapped routes. */
    val errorDecoder: RestErrorDecoder,
) {
    /**
     * Copies [headers] onto a Ktor request builder — usually the first line of a handler's
     * request block.
     *
     * Spelled as a function rather than left to `headers.forEach { }` because inside a request
     * block the nearer `HttpRequestBuilder.headers` shadows this scope's map, which reads as if
     * it should work and doesn't.
     */
    fun HttpRequestBuilder.applyCallHeaders() {
        this@RestCallScope.headers.forEach { (name, value) -> header(name, value) }
    }
}
