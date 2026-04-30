package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcBidirectionalHandler
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcRoute
import dev.isaacudy.udytils.urpc.UrpcStreamingFrame
import dev.isaacudy.udytils.urpc.UrpcStreamingHandler
import dev.isaacudy.udytils.urpc.UrpcUnaryHandler
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import kotlinx.serialization.KSerializer
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

internal class KtorUrpcRoute(
    private val route: Route,
    private val errorMapper: ServiceErrorMapper,
    private val logger: UrpcLogger,
) : UrpcRoute {

    override fun <Req, Res> installUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        handler: UrpcUnaryHandler<Req, Res>,
    ) {
        route.post("/services/${descriptor.name}") {
            try {
                @Suppress("UNCHECKED_CAST")
                val request: Req = if (descriptor.isUnitRequest) {
                    Unit as Req
                } else {
                    serviceFunctionJson.decodeFromString(descriptor.requestSerializer, call.receive<String>())
                }
                val response = handler(request)
                if (descriptor.isUnitResponse) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        serviceFunctionJson.encodeToString(descriptor.responseSerializer, response),
                    )
                }
            } catch (t: Throwable) {
                handleServiceError(call, t, errorMapper, logger)
            }
        }
    }

    override fun <Req, Res> installStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        handler: UrpcStreamingHandler<Req, Res>,
    ) {
        route.webSocket("/streamingServices/${descriptor.name}") {
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
                    send(Frame.Text(encodeStreamingFrame(descriptor.responseSerializer, response)))
                }
                // Handler's flow completed naturally — signal graceful end-of-stream
                // so the client doesn't think the WS closed unexpectedly and try to
                // reconnect.
                send(Frame.Text(encodeCompleteFrame(descriptor.responseSerializer)))
            } catch (t: Throwable) {
                handleStreamingError(this, descriptor.name, descriptor.responseSerializer, t, errorMapper, logger)
            }
        }
    }

    override fun <Req, Res> installBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        handler: UrpcBidirectionalHandler<Req, Res>,
    ) {
        route.webSocket("/streamingServices/${descriptor.name}") {
            try {
                // Channel-based plumbing so the request flow handed to the handler
                // actually completes when the client closes the socket. (Earlier
                // versions used a SharedFlow, which never completes — leaving
                // `handler(...)` waiting on an infinite source even after end-of-
                // stream, hanging the bidi session forever.)
                val requestsChannel = Channel<Req>(capacity = Channel.BUFFERED)
                coroutineScope {
                    val readerJob = launch {
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    requestsChannel.send(
                                        serviceFunctionJson.decodeFromString(
                                            descriptor.requestSerializer,
                                            frame.readText(),
                                        )
                                    )
                                }
                            }
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            if (t !is ClosedReceiveChannelException) throw t
                        } finally {
                            // Frame loop ended — close the Channel so the handler's
                            // request Flow completes.
                            requestsChannel.close()
                        }
                    }
                    try {
                        handler(requestsChannel.consumeAsFlow()).collect { response ->
                            send(Frame.Text(encodeStreamingFrame(descriptor.responseSerializer, response)))
                        }
                        // Handler's response flow completed naturally — signal
                        // graceful end-of-stream to the client.
                        send(Frame.Text(encodeCompleteFrame(descriptor.responseSerializer)))
                    } finally {
                        readerJob.cancel()
                    }
                }
            } catch (t: Throwable) {
                handleStreamingError(this, descriptor.name, descriptor.responseSerializer, t, errorMapper, logger)
            }
        }
    }
}

private fun <Res> encodeStreamingFrame(
    responseSerializer: KSerializer<Res>,
    response: Res,
): String = serviceFunctionJson.encodeToString(
    UrpcStreamingFrame.serializer(responseSerializer),
    UrpcStreamingFrame.Data(response),
)

private fun <Res> encodeCompleteFrame(
    responseSerializer: KSerializer<Res>,
): String = serviceFunctionJson.encodeToString(
    UrpcStreamingFrame.serializer(responseSerializer),
    UrpcStreamingFrame.Complete,
)

private suspend fun <Res> handleStreamingError(
    session: WebSocketServerSession,
    name: String,
    responseSerializer: KSerializer<Res>,
    t: Throwable,
    errorMapper: ServiceErrorMapper,
    logger: UrpcLogger,
) {
    if (t is CancellationException) throw t
    if (t is ClosedReceiveChannelException) throw t
    val status = errorMapper.mapStatus(t)
    if (status.value >= 500) {
        logger.error("Streaming error on $name", t)
    } else {
        logger.debug("Streaming error on $name: ${status.value} ${t::class.simpleName}: ${t.message}")
    }
    runCatching {
        val errorFrame: UrpcStreamingFrame<Res> = UrpcStreamingFrame.Error(
            error = ServiceError.from(t),
            statusCode = status.value,
        )
        session.send(
            Frame.Text(
                serviceFunctionJson.encodeToString(
                    UrpcStreamingFrame.serializer(responseSerializer),
                    errorFrame,
                )
            )
        )
    }
    session.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, t.message ?: "Internal error"))
}
