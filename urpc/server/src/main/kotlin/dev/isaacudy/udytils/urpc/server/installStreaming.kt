package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcBidirectionalHandler
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcStreamingHandler
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.server.routing.Route
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

// TODO(urpc): the KSP processor does NOT yet generate code for streaming functions.
// These install helpers exist so manual descriptors still work — once KSP gains
// streaming support, the generated `_installUrpc(...)` will call into these.

/**
 * Registers a server-streaming WebSocket route for [descriptor].
 */
fun <Req, Res> Route.installStreaming(
    descriptor: StreamingServiceDescriptor<Req, Res>,
    logger: UrpcLogger = UrpcLogger.NoOp,
    handler: UrpcStreamingHandler<Req, Res>,
) {
    webSocket("/streamingServices/${descriptor.name}") {
        try {
            @Suppress("UNCHECKED_CAST")
            val request: Req = if (descriptor.isUnitRequest) {
                Unit as Req
            } else {
                val frame = incoming.receive()
                val text = (frame as? Frame.Text)?.readText()
                    ?: throw IllegalArgumentException("Expected text frame with request")
                serviceFunctionJson.decodeFromString(descriptor.requestSerializer, text)
            }
            handler(request).collect { response ->
                send(Frame.Text(serviceFunctionJson.encodeToString(descriptor.responseSerializer, response)))
            }
        } catch (t: Throwable) {
            handleStreamingError(this, descriptor.name, t, logger)
        }
    }
}

/**
 * Registers a bidirectional-streaming WebSocket route for [descriptor].
 */
fun <Req, Res> Route.installBidirectional(
    descriptor: BidirectionalServiceDescriptor<Req, Res>,
    logger: UrpcLogger = UrpcLogger.NoOp,
    handler: UrpcBidirectionalHandler<Req, Res>,
) {
    webSocket("/streamingServices/${descriptor.name}") {
        try {
            val requests = MutableSharedFlow<Req>(replay = 1)
            coroutineScope {
                launch {
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                requests.emit(serviceFunctionJson.decodeFromString(descriptor.requestSerializer, frame.readText()))
                            }
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        if (t is ClosedReceiveChannelException) return@launch
                        throw t
                    }
                }
                handler(requests).collect { response ->
                    send(Frame.Text(serviceFunctionJson.encodeToString(descriptor.responseSerializer, response)))
                }
            }
        } catch (t: Throwable) {
            handleStreamingError(this, descriptor.name, t, logger)
        }
    }
}

private suspend fun handleStreamingError(
    session: WebSocketServerSession,
    name: String,
    t: Throwable,
    logger: UrpcLogger,
) {
    if (t is CancellationException) throw t
    if (t is ClosedReceiveChannelException) throw t
    logger.error("Streaming error on $name", t)
    runCatching {
        session.send(
            Frame.Text(
                "{\"error\":${
                    serviceFunctionJson.encodeToString(
                        ServiceError.serializer(),
                        ServiceError.from(t),
                    )
                }}"
            )
        )
    }
    session.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, t.message ?: "Internal error"))
}
