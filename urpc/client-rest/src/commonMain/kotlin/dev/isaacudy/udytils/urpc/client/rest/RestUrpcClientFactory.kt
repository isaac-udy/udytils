package dev.isaacudy.udytils.urpc.client.rest

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcCallContext
import dev.isaacudy.udytils.urpc.UrpcCallKind
import dev.isaacudy.udytils.urpc.UrpcClientFactory
import dev.isaacudy.udytils.urpc.UrpcClientInterceptor
import dev.isaacudy.udytils.urpc.UrpcLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * A [UrpcClientFactory] backed by a plain-JSON REST API instead of a urpc server.
 *
 * Built with [restUrpcClient]; see that function for the whole story. Exposed as a type only so
 * that [mappedWireNames] is reachable — everything else about it is the [UrpcClientFactory]
 * contract.
 */
class RestUrpcClientFactory internal constructor(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val routes: Map<String, ConfiguredRestRoute>,
    private val servicePrefixes: Set<String>,
    private val json: Json,
    private val interceptors: List<UrpcClientInterceptor>,
    private val tokenRefresher: (suspend () -> Unit)?,
    private val errorDecoder: RestErrorDecoder,
    private val fallback: UrpcClientFactory?,
    private val logger: UrpcLogger,
) : UrpcClientFactory {

    /**
     * The urpc wire names this client serves over REST. Everything else is delegated to the
     * fallback factory, or fails if there is none.
     *
     * Assert against it in a test to prove the route table covers the contract — that is what
     * makes deleting routes during a migration safe, because a wire name that neither side covers
     * fails the test instead of 404-ing in production.
     */
    val mappedWireNames: Set<String> get() = routes.keys

    override suspend fun <Req, Res> callUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
    ): Res {
        val configured = routes[descriptor.name]
        if (configured == null) {
            val fallback = fallback ?: throw unmapped(descriptor.name)
            logger.debug("urpc call '${descriptor.name}' is not mapped to REST; delegating to fallback")
            return fallback.callUnary(descriptor, request)
        }
        return when (val route = configured.route) {
            is RestRoute.Custom -> callCustom(descriptor, request, configured, route)
            is RestRoute.Http -> callHttp(descriptor, request, configured, route)
        }
    }

    /**
     * Streaming has no REST equivalent, so a wire name is either unmapped — and delegated — or a
     * mapping mistake. Both are reported eagerly rather than on collection: the caller is holding
     * a `Flow` it can never get a value from, and a stack trace at the call site is far easier to
     * act on than one at whatever point downstream first collects.
     */
    override fun <Req, Res> callStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        request: Req,
    ): Flow<Res> {
        val configured = routes[descriptor.name] ?: return delegateStreaming(descriptor.name) {
            it.callStreaming(descriptor, request)
        }
        throw streamingUnsupported(descriptor.name, configured)
    }

    /** @see callStreaming */
    override fun <Req, Res> callBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        requests: Flow<Req>,
    ): Flow<Res> {
        val configured = routes[descriptor.name] ?: return delegateStreaming(descriptor.name) {
            it.callBidirectional(descriptor, requests)
        }
        throw streamingUnsupported(descriptor.name, configured)
    }

    private fun <Res> delegateStreaming(wireName: String, call: (UrpcClientFactory) -> Flow<Res>): Flow<Res> {
        val fallback = fallback ?: throw unmapped(wireName)
        logger.debug("urpc call '$wireName' is not mapped to REST; delegating to fallback")
        return call(fallback)
    }

    // --- mapped HTTP routes ---

    private suspend fun <Req, Res> callHttp(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
        configured: ConfiguredRestRoute,
        route: RestRoute.Http,
    ): Res {
        val encoded = encodeRestRequest(descriptor, request, route, json)
        val response = execute(route, encoded, headers(descriptor.name, configured))
        val refresher = tokenRefresher
        if (response.status == HttpStatusCode.Unauthorized && refresher != null) {
            logger.debug("urpc call '${descriptor.name}' returned 401; refreshing and retrying once")
            // Swallowed like the native client does: a refresher that fails still gets the retry,
            // so the error the caller sees is the API's second 401, not the refresher's problem.
            runCatching { refresher() }
            // Re-run the chain so the retry picks up whatever the refresher produced.
            val retried = execute(route, encoded, headers(descriptor.name, configured))
            return decode(descriptor, route, retried)
        }
        return decode(descriptor, route, response)
    }

    private suspend fun execute(
        route: RestRoute.Http,
        encoded: RestHttpRequest,
        requestHeaders: Map<String, String>,
    ): HttpResponse = httpClient.request("$baseUrl${encoded.path}") {
        method = route.method
        requestHeaders.forEach { (name, value) -> header(name, value) }
        encoded.query.forEach { (name, value) -> parameter(name, value) }
        if (encoded.body != null) {
            contentType(ContentType.Application.Json)
            setBody(encoded.body)
        }
    }

    private suspend fun <Req, Res> decode(
        descriptor: ServiceDescriptor<Req, Res>,
        route: RestRoute.Http,
        response: HttpResponse,
    ): Res {
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw errorDecoder.decode(response.status.value, body)
        }
        if (descriptor.isUnitResponse) {
            @Suppress("UNCHECKED_CAST")
            return Unit as Res
        }
        val text = response.bodyAsText()
        val parsed = runCatching { json.parseToJsonElement(text) }.getOrElse { cause ->
            throw RestUrpcException(
                "REST route '$route' for urpc call '${descriptor.name}' returned " +
                    "${response.status.value} with a body that is not JSON.",
                cause,
            )
        }
        val transformed = route.responseTransform?.let { transform ->
            runCatching { transform(parsed) }.getOrElse { cause ->
                throw RestUrpcException(
                    "The `response { }` transform on REST route '$route' for urpc call " +
                        "'${descriptor.name}' failed on the response body.",
                    cause,
                )
            }
        } ?: parsed
        return runCatching { json.decodeFromJsonElement(descriptor.responseSerializer, transformed) }
            .getOrElse { cause ->
                throw RestUrpcException(
                    "REST route '$route' for urpc call '${descriptor.name}' returned a body the " +
                        "contract's response type cannot read. Reshape it with `response { }`, or " +
                        "map the call with `custom { }`.",
                    cause,
                )
            }
    }

    // --- custom routes ---

    private suspend fun <Req, Res> callCustom(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
        configured: ConfiguredRestRoute,
        route: RestRoute.Custom,
    ): Res {
        val scope = RestCallScope(
            httpClient = httpClient,
            baseUrl = baseUrl,
            json = json,
            wireName = descriptor.name,
            headers = headers(descriptor.name, configured),
            errorDecoder = errorDecoder,
        )
        @Suppress("UNCHECKED_CAST")
        return route.handler(scope, request) as Res
    }

    // --- shared ---

    /**
     * Runs the interceptor chain for one attempt at a call; the resulting metadata becomes
     * request headers, overriding the service's static headers where the names collide (a static
     * header is a default; interceptor metadata is computed fresh for this attempt).
     */
    private suspend fun headers(wireName: String, configured: ConfiguredRestRoute): Map<String, String> {
        val context = UrpcCallContext(wireName, UrpcCallKind.UNARY)
        interceptors.forEach { it.interceptOpen(context) }
        return configured.staticHeaders + context.metadata
    }

    private fun unmapped(wireName: String) = RestUrpcException(
        "urpc call '$wireName' has no REST route and no fallback UrpcClientFactory was supplied. " +
            "Configured service prefixes: ${servicePrefixes.sorted()}. Add a route for it, or " +
            "pass `fallback = httpClient.urpcClient(baseUrl)` so calls the REST API doesn't serve " +
            "reach the urpc server.",
    )

    private fun streamingUnsupported(wireName: String, configured: ConfiguredRestRoute) =
        UnsupportedOperationException(
            "urpc call '$wireName' is mapped to REST route '${configured.route}', but REST routes " +
                "— including `custom { }` — can only serve unary calls. Remove the mapping so the " +
                "call reaches the fallback UrpcClientFactory, or model the call as unary in the " +
                "contract until the backend can serve a stream.",
        )
}
