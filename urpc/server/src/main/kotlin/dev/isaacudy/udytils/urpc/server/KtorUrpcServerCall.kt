package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcServerCall
import dev.isaacudy.udytils.urpc.UrpcStreamingFrame
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

internal class KtorUrpcServerCall(
    override val wireName: String,
    val applicationCall: ApplicationCall,
    private val webSocketSession: WebSocketServerSession?,
    private val errorMapper: ServiceErrorMapper,
    private val logger: UrpcLogger,
) : UrpcServerCall {

    override suspend fun <Req, Res> handleUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        invoke: suspend (Req) -> Res,
    ) {
        try {
            @Suppress("UNCHECKED_CAST")
            val request: Req = if (descriptor.isUnitRequest) {
                Unit as Req
            } else {
                serviceFunctionJson.decodeFromString(descriptor.requestSerializer, applicationCall.receive<String>())
            }
            val response = invoke(request)
            if (descriptor.isUnitResponse) {
                applicationCall.respond(HttpStatusCode.NoContent)
            } else {
                applicationCall.respond(
                    HttpStatusCode.OK,
                    serviceFunctionJson.encodeToString(descriptor.responseSerializer, response),
                )
            }
        } catch (t: Throwable) {
            handleServiceError(applicationCall, t, errorMapper, logger)
        }
    }

    override suspend fun <Req, Res> handleStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        invoke: (Req) -> Flow<Res>,
    ) {
        val session = requireWebSocketSession(descriptor.name)
        try {
            @Suppress("UNCHECKED_CAST")
            val request: Req = if (descriptor.isUnitRequest) {
                Unit as Req
            } else {
                val frame = session.incoming.receive()
                val text = (frame as? Frame.Text)?.readText()
                    ?: throw IllegalArgumentException("Expected text frame with request")
                serviceFunctionJson.decodeFromString(descriptor.requestSerializer, text)
            }
            invoke(request).collect { response ->
                session.send(Frame.Text(encodeStreamingFrame(descriptor.responseSerializer, response)))
            }
            session.send(Frame.Text(encodeCompleteFrame(descriptor.responseSerializer)))
        } catch (t: Throwable) {
            handleStreamingError(session, descriptor.name, descriptor.responseSerializer, t, errorMapper, logger)
        }
    }

    override suspend fun <Req, Res> handleBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        invoke: (Flow<Req>) -> Flow<Res>,
    ) {
        val session = requireWebSocketSession(descriptor.name)
        try {
            val requestsChannel = Channel<Req>(capacity = Channel.BUFFERED)
            coroutineScope {
                val readerJob = launch {
                    try {
                        for (frame in session.incoming) {
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
                        requestsChannel.close()
                    }
                }
                try {
                    invoke(requestsChannel.consumeAsFlow()).collect { response ->
                        session.send(Frame.Text(encodeStreamingFrame(descriptor.responseSerializer, response)))
                    }
                    session.send(Frame.Text(encodeCompleteFrame(descriptor.responseSerializer)))
                } finally {
                    readerJob.cancel()
                }
            }
        } catch (t: Throwable) {
            handleStreamingError(session, descriptor.name, descriptor.responseSerializer, t, errorMapper, logger)
        }
    }

    private fun requireWebSocketSession(wireName: String): WebSocketServerSession =
        webSocketSession ?: error(
            "Service '$wireName' is a streaming function but the call landed on the unary HTTP route. " +
                    "Make sure the client and server agree on which route to use for streaming calls."
        )
}

/**
 * Recovers the underlying Ktor [ApplicationCall] from a [UrpcServerCall].
 * Use this from `UrpcService.accepts` or other server-side code that needs
 * access to request headers, query params, etc.
 */
val UrpcServerCall.applicationCall: ApplicationCall
    get() = (this as KtorUrpcServerCall).applicationCall

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
