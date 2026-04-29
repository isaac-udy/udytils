package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcClientFactory
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

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
            try {
                httpClient.webSocket(urlString = buildWebSocketUrl(descriptor.name)) {
                    retryDelay = 1_000L
                    if (!descriptor.isUnitRequest) {
                        send(Frame.Text(serviceFunctionJson.encodeToString(descriptor.requestSerializer, request)))
                    }
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            send(decodeStreamingFrame(descriptor.responseSerializer, frame.readText()))
                        }
                    }
                }
                logger.debug("WebSocket closed for ${descriptor.name}, reconnecting...")
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

    override fun <Req, Res> callBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        requests: Flow<Req>,
    ): Flow<Res> = channelFlow {
        val latestRequest = MutableSharedFlow<Req>(replay = 1)
        launch { requests.collect { latestRequest.emit(it) } }

        var retryDelay = 1_000L
        while (isActive) {
            try {
                httpClient.webSocket(urlString = buildWebSocketUrl(descriptor.name)) {
                    retryDelay = 1_000L
                    coroutineScope {
                        launch {
                            latestRequest.collect { request ->
                                send(Frame.Text(serviceFunctionJson.encodeToString(descriptor.requestSerializer, request)))
                            }
                        }
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                send(decodeStreamingFrame(descriptor.responseSerializer, frame.readText()))
                            }
                        }
                    }
                }
                logger.debug("Bidirectional WebSocket closed for ${descriptor.name}, reconnecting...")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (t is ServiceException) throw t
                logger.warn("Bidirectional WebSocket error for ${descriptor.name}: ${t.message}, retrying in ${retryDelay}ms")
            }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
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
    ): Res {
        // TODO(urpc): same envelope-collision concern as before — payloads with an
        // "error" field could be misinterpreted. Replace with a tagged envelope.
        val jsonObj = runCatching { serviceFunctionJson.decodeFromString(JsonObject.serializer(), text) }.getOrNull()
        if (jsonObj != null && jsonObj.containsKey("error")) {
            val errObj = jsonObj["error"]
            if (errObj is JsonObject) {
                val error = runCatching {
                    serviceFunctionJson.decodeFromJsonElement(ServiceError.serializer(), errObj)
                }.getOrNull()
                throw ServiceException(
                    statusCode = 500,
                    errorType = error?.type,
                    errorMessage = error?.message ?: ErrorMessage(
                        title = "Streaming Error",
                        message = "An unknown error occurred",
                    ),
                )
            }
        }
        return serviceFunctionJson.decodeFromString(responseSerializer, text)
    }

    private fun buildWebSocketUrl(serviceName: String): String {
        val url = URLBuilder(baseUrl)
        url.protocol = if (url.protocol == URLProtocol.HTTPS) URLProtocol.WSS else URLProtocol.WS
        url.pathSegments = url.pathSegments + listOf("streamingServices") + serviceName.split("/")
        authTokenProvider()?.let { url.parameters.append("token", it) }
        return url.buildString()
    }
}
