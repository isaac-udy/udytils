package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.UrpcFrame
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcServerCall
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers the urpc routes under [rootPath] and invokes [handler] for every incoming call.
 *
 * The handler receives a [UrpcServerCall] and is responsible for finding the right [UrpcService]
 * for the call and invoking [UrpcService.handle] on it. The typical Koin-backed shape:
 *
 * ```
 * routing {
 *     urpc { call ->
 *         val service = call.applicationCall.scope.getAll<UrpcService>()
 *             .firstOrNull { it.accepts(call) }
 *             ?: return@urpc call.applicationCall.respond(HttpStatusCode.NotFound)
 *         service.handle(call)
 *     }
 * }
 * ```
 *
 * Two routes are registered:
 * - `POST ${rootPath}/services/{wireName}` — one HTTP request/response per unary call.
 * - `WS   ${rootPath}/urpc` — a **single multiplexed socket** carrying all streaming and
 *   bidirectional calls. Each client `Open` frame starts a logical call (its own coroutine and
 *   [UrpcServerCall]); [handler] is invoked once per `Open`. Responses are tagged with the call id
 *   and written back through the shared, write-serialised socket, so concurrent calls don't
 *   interleave mid-frame and one call's completion/error doesn't disturb the others.
 *
 * The host must install Ktor's `WebSockets` plugin if any streaming services will be served.
 */
fun Route.urpc(
    rootPath: String = "",
    errorMapper: ServiceErrorMapper = ServiceErrorMapper.Default,
    logger: UrpcLogger = UrpcLogger.NoOp,
    handler: suspend (UrpcServerCall) -> Unit,
) {
    if (rootPath.isEmpty()) {
        registerUrpcRoutes(this, errorMapper, logger, handler)
    } else {
        route(rootPath) {
            registerUrpcRoutes(this, errorMapper, logger, handler)
        }
    }
}

private class MuxCall(val job: Job, val requests: SendChannel<JsonElement>)

private fun registerUrpcRoutes(
    route: Route,
    errorMapper: ServiceErrorMapper,
    logger: UrpcLogger,
    handler: suspend (UrpcServerCall) -> Unit,
) {
    // Unary calls — plain HTTP request/response.
    route.post("/services/{wireName}") {
        val wireName = call.parameters["wireName"] ?: return@post
        handler(KtorUrpcServerCall(wireName, call, errorMapper, logger))
    }

    // Streaming + bidirectional calls — all multiplexed over a single WebSocket.
    route.webSocket("/urpc") {
        val session = this
        val sendMutex = Mutex()
        suspend fun send(frame: UrpcFrame) = sendMutex.withLock {
            session.send(Frame.Text(serviceFunctionJson.encodeToString(UrpcFrame.serializer(), frame)))
        }

        val calls = ConcurrentHashMap<Long, MuxCall>()
        coroutineScope {
            try {
                for (frame in session.incoming) {
                    if (frame !is Frame.Text) continue
                    when (val urpcFrame = serviceFunctionJson.decodeFromString(UrpcFrame.serializer(), frame.readText())) {
                        is UrpcFrame.Open -> {
                            val requests = Channel<JsonElement>(Channel.BUFFERED)
                            val job = launch {
                                try {
                                    handler(MuxUrpcServerCall(urpcFrame, session.call, requests, ::send, errorMapper, logger))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    // A handler-level failure (DI resolution, a 404 respond on the
                                    // upgraded WS, a generated-binding error, …) must end only THIS
                                    // call — never cancel siblings or tear down the shared socket.
                                    logger.error("urpc server: handler failed for call ${urpcFrame.callId} (${urpcFrame.wireName})", t)
                                    runCatching {
                                        send(UrpcFrame.Error(urpcFrame.callId, ServiceError.from(t), errorMapper.mapStatus(t).value))
                                    }
                                } finally {
                                    calls.remove(urpcFrame.callId)
                                    requests.close()
                                }
                            }
                            calls[urpcFrame.callId] = MuxCall(job, requests)
                        }

                        is UrpcFrame.Cancel -> calls.remove(urpcFrame.callId)?.let {
                            it.requests.close()
                            it.job.cancel()
                        }

                        is UrpcFrame.ClientData -> calls[urpcFrame.callId]?.let { mux ->
                            // Don't silently drop a request item on overflow — fail this one call
                            // loudly so the client learns its request stream was truncated.
                            if (mux.requests.trySend(urpcFrame.payload).isFailure) {
                                runCatching {
                                    send(
                                        UrpcFrame.Error(
                                            urpcFrame.callId,
                                            ServiceError.from(IllegalStateException("request backpressure exceeded")),
                                            500,
                                        ),
                                    )
                                }
                                calls.remove(urpcFrame.callId)
                                mux.requests.close()
                                mux.job.cancel()
                            }
                        }
                        is UrpcFrame.ClientComplete -> calls[urpcFrame.callId]?.requests?.close()

                        // Data / Error / Complete / Auth are not expected from the client here.
                        else -> logger.debug("urpc server: ignoring frame ${urpcFrame::class.simpleName}")
                    }
                }
            } finally {
                // Socket closed — cancel every in-flight call so no handler outlives the connection.
                calls.values.forEach { it.job.cancel() }
            }
        }
    }
}
