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
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

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
 *
 * @param idleTimeout When non-null, connections with no application-frame activity (neither
 *   inbound nor outbound) for this duration are closed with close code 4008 ("idle"). Ping/pong
 *   frames are handled by Ktor before reaching the read loop and do NOT reset the idle timer.
 *   The client can treat 4008 as a clean close and reconnect without backoff.
 */
fun Route.urpc(
    rootPath: String = "",
    errorMapper: ServiceErrorMapper = ServiceErrorMapper.Default,
    logger: UrpcLogger = UrpcLogger.NoOp,
    idleTimeout: Duration? = null,
    handler: suspend (UrpcServerCall) -> Unit,
) {
    if (rootPath.isEmpty()) {
        registerUrpcRoutes(this, errorMapper, logger, idleTimeout, handler)
    } else {
        route(rootPath) {
            registerUrpcRoutes(this, errorMapper, logger, idleTimeout, handler)
        }
    }
}

/** Close code for server-initiated idle disconnect (private-use range). */
const val IDLE_CLOSE_CODE: Short = 4008
const val IDLE_CLOSE_REASON: String = "idle"

private class MuxCall(val job: Job, val requests: SendChannel<JsonElement>)

private fun registerUrpcRoutes(
    route: Route,
    errorMapper: ServiceErrorMapper,
    logger: UrpcLogger,
    idleTimeout: Duration?,
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

        // Idle tracking: AtomicLong updated on every application frame (read or sent).
        // System.currentTimeMillis is sufficient — the watchdog polls every 30s.
        val lastActivityMs = AtomicLong(System.currentTimeMillis())
        fun touchActivity() { lastActivityMs.set(System.currentTimeMillis()) }

        suspend fun send(frame: UrpcFrame) = sendMutex.withLock {
            touchActivity()
            session.send(Frame.Text(serviceFunctionJson.encodeToString(UrpcFrame.serializer(), frame)))
        }

        val calls = ConcurrentHashMap<Long, MuxCall>()
        coroutineScope {
            // Idle watchdog: polls every 30s and closes the session when idle exceeds the timeout.
            if (idleTimeout != null) {
                val timeoutMs = idleTimeout.inWholeMilliseconds
                launch {
                    while (isActive) {
                        delay(IDLE_POLL_INTERVAL_MS)
                        val elapsed = System.currentTimeMillis() - lastActivityMs.get()
                        if (elapsed >= timeoutMs) {
                            logger.debug("urpc server: closing idle connection (${elapsed}ms idle)")
                            session.close(CloseReason(IDLE_CLOSE_CODE, IDLE_CLOSE_REASON))
                            break
                        }
                    }
                }
            }

            try {
                for (frame in session.incoming) {
                    if (frame !is Frame.Text) continue
                    touchActivity()
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

private const val IDLE_POLL_INTERVAL_MS = 30_000L
