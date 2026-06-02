package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcCallContext
import dev.isaacudy.udytils.urpc.UrpcCallKind
import dev.isaacudy.udytils.urpc.UrpcClientFactory
import dev.isaacudy.udytils.urpc.UrpcClientInterceptor
import dev.isaacudy.udytils.urpc.UrpcFrame
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Ktor-backed [UrpcClientFactory].
 *
 * Unary calls are plain HTTP POSTs to `/services/{name}` (401 → refresh → retry once); streaming
 * and bidirectional calls are multiplexed over a single `/urpc` WebSocket managed by
 * [UrpcConnection]. Auth and other per-call metadata are supplied by [interceptors] — the same
 * chain runs for unary (metadata → request headers) and streaming (metadata → `Open` frame).
 */
internal class KtorUrpcClientFactory(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    scope: CoroutineScope,
    private val tokenRefresher: suspend () -> Unit,
    private val interceptors: List<UrpcClientInterceptor>,
    private val logger: UrpcLogger,
) : UrpcClientFactory {

    private val connection = UrpcConnection(
        scope = scope,
        transport = KtorTransport(),
        interceptors = interceptors,
        logger = logger,
    )

    override suspend fun <Req, Res> callUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
    ): Res {
        val result = executeUnary(descriptor, request, unaryMetadata(descriptor.name))
        if (result.status == HttpStatusCode.Unauthorized) {
            runCatching { tokenRefresher() }
            // Re-run the interceptor chain so the retry picks up any refreshed token.
            return parseUnaryResponse(descriptor, executeUnary(descriptor, request, unaryMetadata(descriptor.name)))
        }
        return parseUnaryResponse(descriptor, result)
    }

    override fun <Req, Res> callStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        request: Req,
    ): Flow<Res> = connection.openStreaming(descriptor, request)

    override fun <Req, Res> callBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        requests: Flow<Req>,
    ): Flow<Res> = connection.openBidirectional(descriptor, requests)

    // --- unary (HTTP) ---

    /** Runs the interceptor chain for a unary call; the resulting metadata becomes request headers. */
    private suspend fun unaryMetadata(wireName: String): Map<String, String> {
        val context = UrpcCallContext(wireName, UrpcCallKind.UNARY)
        interceptors.forEach { it.interceptOpen(context) }
        return context.metadata
    }

    private suspend fun <Req, Res> executeUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
        headers: Map<String, String>,
    ): HttpResponse {
        return httpClient.post("$baseUrl/services/${descriptor.name}") {
            headers.forEach { (name, value) -> header(name, value) }
            if (!descriptor.isUnitRequest) {
                contentType(ContentType.Application.Json)
                setBody(serviceFunctionJson.encodeToString(descriptor.requestSerializer, request))
            }
        }
    }

    private suspend fun <Req, Res> parseUnaryResponse(
        descriptor: ServiceDescriptor<Req, Res>,
        response: HttpResponse,
    ): Res {
        if (!response.status.isSuccess()) throwServiceError(response)
        if (descriptor.isUnitResponse) {
            @Suppress("UNCHECKED_CAST")
            return Unit as Res
        }
        return serviceFunctionJson.decodeFromString(descriptor.responseSerializer, response.bodyAsText())
    }

    private suspend fun throwServiceError(response: HttpResponse): Nothing {
        val body = runCatching { response.bodyAsText() }.getOrNull()
        val error = body?.let {
            runCatching { serviceFunctionJson.decodeFromString(ServiceError.serializer(), it) }.getOrNull()
        }
        throw ServiceException(
            statusCode = response.status.value,
            errorType = error?.type,
            errorMessage = error?.message ?: ErrorMessage(
                title = "HTTP ${response.status.value}",
                message = "An unknown error occurred",
            ),
        )
    }

    // --- streaming transport (single multiplexed WebSocket) ---

    private inner class KtorTransport : UrpcConnectionTransport {
        override suspend fun run(
            outgoing: ReceiveChannel<UrpcFrame>,
            incoming: SendChannel<UrpcFrame>,
        ) {
            httpClient.webSocket(urlString = buildWebSocketUrl()) {
                coroutineScope {
                    val sender = launch {
                        for (frame in outgoing) {
                            send(Frame.Text(serviceFunctionJson.encodeToString(UrpcFrame.serializer(), frame)))
                        }
                    }
                    try {
                        for (frame in this@webSocket.incoming) {
                            if (frame !is Frame.Text) continue
                            incoming.send(serviceFunctionJson.decodeFromString(UrpcFrame.serializer(), frame.readText()))
                        }
                    } finally {
                        sender.cancel()
                    }
                }
            }
        }
    }

    private fun buildWebSocketUrl(): String {
        val url = URLBuilder(baseUrl)
        url.protocol = if (url.protocol == URLProtocol.HTTPS) URLProtocol.WSS else URLProtocol.WS
        url.pathSegments = url.pathSegments + listOf("urpc")
        return url.buildString()
    }
}
