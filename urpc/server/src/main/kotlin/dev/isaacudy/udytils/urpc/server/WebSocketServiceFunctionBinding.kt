package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.StreamingServiceFunction
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

class WebSocketServiceFunctionBinding<Func : StreamingServiceFunction<Request, Response>, Request, Response>(
    override val name: String,
    private val requestSerializer: KSerializer<Request>,
    private val responseSerializer: KSerializer<Response>,
    private val isUnitRequest: Boolean,
    private val logger: UrpcLogger,
    private val handler: (Request) -> Flow<Response>,
) : StreamingServiceFunctionBinding<Func> {
    override suspend fun handle(socketSession: WebSocketServerSession) {
        try {
            val request: Request = if (isUnitRequest) {
                @Suppress("UNCHECKED_CAST")
                Unit as Request
            } else {
                val frame = socketSession.incoming.receive()
                val text = (frame as? Frame.Text)?.readText()
                    ?: throw IllegalArgumentException("Expected text frame with request")
                serviceFunctionJson.decodeFromString(requestSerializer, text)
            }
            handler(request).collect { response ->
                socketSession.send(
                    Frame.Text(serviceFunctionJson.encodeToString(responseSerializer, response))
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (t is ClosedReceiveChannelException) throw t
            logger.error("Streaming error on WebSocket $name", t)
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
