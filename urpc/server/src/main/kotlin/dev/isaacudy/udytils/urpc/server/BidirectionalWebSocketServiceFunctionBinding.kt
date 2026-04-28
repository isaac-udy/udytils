package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.BidirectionalStreamingServiceFunction
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

class BidirectionalWebSocketServiceFunctionBinding<Func : BidirectionalStreamingServiceFunction<Request, Response>, Request, Response>(
    override val name: String,
    private val requestSerializer: KSerializer<Request>,
    private val responseSerializer: KSerializer<Response>,
    private val logger: UrpcLogger,
    private val handler: (Flow<Request>) -> Flow<Response>,
) : BidirectionalStreamingServiceFunctionBinding<Func> {
    override suspend fun handle(socketSession: WebSocketServerSession) {
        try {
            val requests = MutableSharedFlow<Request>(replay = 1)
            coroutineScope {
                launch {
                    try {
                        for (frame in socketSession.incoming) {
                            if (frame is Frame.Text) {
                                val request = serviceFunctionJson.decodeFromString(
                                    requestSerializer,
                                    frame.readText(),
                                )
                                requests.emit(request)
                            }
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        if (t is ClosedReceiveChannelException) return@launch
                        throw t
                    }
                }
                handler(requests).collect { response ->
                    socketSession.send(
                        Frame.Text(serviceFunctionJson.encodeToString(responseSerializer, response))
                    )
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (t is ClosedReceiveChannelException) throw t
            logger.error("Bidirectional streaming error on WebSocket $name", t)
            runCatching {
                socketSession.send(
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
            socketSession.close(
                CloseReason(CloseReason.Codes.INTERNAL_ERROR, t.message ?: "Internal error")
            )
        }
    }
}
