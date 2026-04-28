package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

@PublishedApi
internal class StreamingServiceFunctionCaller<Request, Response>(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val path: String,
    private val requestSerializer: KSerializer<Request>,
    private val responseSerializer: KSerializer<Response>,
    private val isUnitRequest: Boolean,
    private val authTokenProvider: () -> String?,
    private val logger: UrpcLogger,
) {
    fun stream(request: Request): Flow<Response> = channelFlow {
        var retryDelay = 1_000L
        while (isActive) {
            try {
                httpClient.webSocket(urlString = buildWebSocketUrl()) {
                    retryDelay = 1_000L
                    if (!isUnitRequest) {
                        send(
                            Frame.Text(
                                serviceFunctionJson.encodeToString(requestSerializer, request)
                            )
                        )
                    }
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            send(decodeFrame(frame.readText()))
                        }
                    }
                }
                logger.debug("WebSocket closed for $path, reconnecting...")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (t is ServiceException) throw t
                logger.warn("WebSocket error for $path: ${t.message}, retrying in ${retryDelay}ms")
            }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
        }
    }

    private fun decodeFrame(text: String): Response {
        // TODO(urpc): the wire format conflates payloads and error envelopes. A response
        // payload that legitimately contains an "error" field will be misinterpreted as
        // an error. Replace with a tagged envelope (e.g. {"data": ...} | {"error": ...})
        // or a kotlinx-serialization sealed interface with @JsonClassDiscriminator.
        val jsonObj = runCatching {
            serviceFunctionJson.decodeFromString(JsonObject.serializer(), text)
        }.getOrNull()
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

    private fun buildWebSocketUrl(): String {
        val url = URLBuilder(baseUrl)
        url.protocol = when (url.protocol) {
            URLProtocol.HTTPS -> URLProtocol.WSS
            else -> URLProtocol.WS
        }
        url.pathSegments = listOf("streamingServices") + path.split("/")
        authTokenProvider()?.let { url.parameters.append("token", it) }
        return url.buildString()
    }
}
