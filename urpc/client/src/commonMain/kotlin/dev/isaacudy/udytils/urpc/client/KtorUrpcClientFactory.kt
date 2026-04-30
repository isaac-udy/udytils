package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcClientFactory
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcStreamingFrame
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
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class KtorUrpcClientFactory(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val authTokenProvider: () -> String?,
    private val tokenRefresher: suspend () -> Unit,
    private val logger: UrpcLogger,
) : UrpcClientFactory {

    override suspend fun <Req, Res> callUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
    ): Res {
        val result = executeUnary(descriptor, request)
        if (result.status == HttpStatusCode.Unauthorized) {
            runCatching { tokenRefresher() }
            return parseUnaryResponse(descriptor, executeUnary(descriptor, request))
        }
        return parseUnaryResponse(descriptor, result)
    }

    override fun <Req, Res> callStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        request: Req,
    ): Flow<Res> = channelFlow {
        var retryDelay = 1_000L
        while (isActive) {
            var completedGracefully = false
            try {
                httpClient.webSocket(urlString = buildWebSocketUrl(descriptor.name)) {
                    retryDelay = 1_000L
                    if (!descriptor.isUnitRequest) {
                        send(Frame.Text(serviceFunctionJson.encodeToString(descriptor.requestSerializer, request)))
                    }
                    framesLoop@ for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        when (val decoded = decodeStreamingFrame(descriptor.responseSerializer, frame.readText())) {
                            is UrpcStreamingFrame.Data -> send(decoded.value)
                            is UrpcStreamingFrame.Error -> throw streamingErrorFrameToException(decoded)
                            is UrpcStreamingFrame.Complete -> {
                                completedGracefully = true
                                break@framesLoop
                            }
                        }
                    }
                }
                if (completedGracefully) return@channelFlow
                logger.debug("WebSocket closed for ${descriptor.name} without Complete, reconnecting...")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (t is ServiceException) throw t
                logger.warn("WebSocket error for ${descriptor.name}: ${t.message}, retrying in ${retryDelay}ms")
            }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
        }
    }

    // Bidirectional sessions are inherently stateful — request history and the
    // server's response progress can't be safely replayed on reconnect — so this
    // implementation deliberately does NOT auto-reconnect. If the connection drops
    // the call fails; consumers that want retry semantics can wrap the call in a
    // catch + retry at a higher level (and decide for themselves how to recover the
    // request stream).
    //
    // TODO(urpc): consider an opt-in `reconnect: Boolean` knob on
    // `httpClient.urpcClient(...)` for users whose bidi protocol is naturally
    // idempotent (e.g. "always send the latest pagination state"). Would need a
    // documented contract about what state survives a reconnect.
    override fun <Req, Res> callBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        requests: Flow<Req>,
    ): Flow<Res> = channelFlow {
        try {
            httpClient.webSocket(urlString = buildWebSocketUrl(descriptor.name)) {
                coroutineScope {
                    val sendJob = launch {
                        try {
                            requests.collect { request ->
                                send(Frame.Text(serviceFunctionJson.encodeToString(descriptor.requestSerializer, request)))
                            }
                        } finally {
                            // User's request flow completed (or was cancelled) — close
                            // the WS to signal end-of-stream. Wrap in NonCancellable so
                            // we still send the close frame during cancellation cleanup.
                            withContext(NonCancellable) {
                                runCatching { close(CloseReason(CloseReason.Codes.NORMAL, "request flow ended")) }
                            }
                        }
                    }
                    try {
                        framesLoop@ for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            when (val decoded = decodeStreamingFrame(descriptor.responseSerializer, frame.readText())) {
                                is UrpcStreamingFrame.Data -> send(decoded.value)
                                is UrpcStreamingFrame.Error -> throw streamingErrorFrameToException(decoded)
                                is UrpcStreamingFrame.Complete -> break@framesLoop
                            }
                        }
                    } finally {
                        sendJob.cancel()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is ServiceException) throw t
            logger.warn("Bidirectional WebSocket error for ${descriptor.name}: ${t.message}")
            throw t
        }
    }

    private suspend fun <Req, Res> executeUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
    ): HttpResponse {
        return httpClient.post("$baseUrl/services/${descriptor.name}") {
            authTokenProvider()?.let { header("Authorization", "Bearer $it") }
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

    private fun <Res> decodeStreamingFrame(
        responseSerializer: kotlinx.serialization.KSerializer<Res>,
        text: String,
    ): UrpcStreamingFrame<Res> = serviceFunctionJson.decodeFromString(
        UrpcStreamingFrame.serializer(responseSerializer),
        text,
    )

    private fun streamingErrorFrameToException(frame: UrpcStreamingFrame.Error): ServiceException =
        ServiceException(
            statusCode = frame.statusCode,
            errorType = frame.error.type,
            errorMessage = frame.error.message ?: ErrorMessage(
                title = "Streaming Error",
                message = "An unknown error occurred",
            ),
        )

    private fun buildWebSocketUrl(serviceName: String): String {
        val url = URLBuilder(baseUrl)
        url.protocol = if (url.protocol == URLProtocol.HTTPS) URLProtocol.WSS else URLProtocol.WS
        url.pathSegments = url.pathSegments + listOf("streamingServices") + serviceName.split("/")
        authTokenProvider()?.let { url.parameters.append("token", it) }
        return url.buildString()
    }
}
