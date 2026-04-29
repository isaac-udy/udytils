package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
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

internal class BidirectionalCaller<Req, Res>(
    private val factory: UrpcClientFactory,
    private val descriptor: BidirectionalServiceDescriptor<Req, Res>,
) {
    fun stream(requests: Flow<Req>): Flow<Res> = channelFlow {
        val latestRequest = MutableSharedFlow<Req>(replay = 1)
        launch { requests.collect { latestRequest.emit(it) } }

        var retryDelay = 1_000L
        while (isActive) {
            try {
                factory.httpClient.webSocket(urlString = buildWebSocketUrl()) {
                    retryDelay = 1_000L
                    coroutineScope {
                        launch {
                            latestRequest.collect { request ->
                                send(Frame.Text(serviceFunctionJson.encodeToString(descriptor.requestSerializer, request)))
                            }
                        }
                        for (frame in incoming) {
                            if (frame is Frame.Text) send(decodeFrame(frame.readText()))
                        }
                    }
                }
                factory.logger.debug("Bidirectional WebSocket closed for ${descriptor.name}, reconnecting...")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (t is ServiceException) throw t
                factory.logger.warn("Bidirectional WebSocket error for ${descriptor.name}: ${t.message}, retrying in ${retryDelay}ms")
            }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
        }
    }

    private fun decodeFrame(text: String): Res {
        // TODO(urpc): see envelope-collision TODO in StreamingCaller.
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
        return serviceFunctionJson.decodeFromString(descriptor.responseSerializer, text)
    }

    private fun buildWebSocketUrl(): String {
        val url = URLBuilder(factory.baseUrl)
        url.protocol = if (url.protocol == URLProtocol.HTTPS) URLProtocol.WSS else URLProtocol.WS
        url.pathSegments = listOf("streamingServices") + descriptor.name.split("/")
        factory.authTokenProvider()?.let { url.parameters.append("token", it) }
        return url.buildString()
    }
}
