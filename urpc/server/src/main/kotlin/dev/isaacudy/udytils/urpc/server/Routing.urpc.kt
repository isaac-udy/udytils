package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.UrpcLogger
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException

/**
 * Resolves a [ServiceFunctionBinding] (or its streaming/bidirectional siblings) by
 * wire name, scoped to a particular incoming call. Implementations typically open a
 * per-request DI scope (e.g. with Koin) and look the binding up from that scope so
 * handlers can depend on request-scoped collaborators such as the authenticated user.
 *
 * Each `with*` method receives a `block` that the resolver must invoke with either
 * the resolved binding or `null` if the name is unknown. Implementations can use the
 * surrounding `try { ... } finally { ... }` to manage scope lifecycle.
 */
interface UrpcBindingResolver {
    suspend fun withServiceFunction(
        call: ApplicationCall,
        name: String,
        block: suspend (ServiceFunctionBinding<*>?) -> Unit,
    )

    suspend fun withStreamingServiceFunction(
        socketSession: WebSocketServerSession,
        name: String,
        block: suspend (
            unidirectional: StreamingServiceFunctionBinding<*>?,
            bidirectional: BidirectionalStreamingServiceFunctionBinding<*>?,
        ) -> Unit,
    )
}

/**
 * Convenience [UrpcBindingResolver] backed by static lists of bindings. No per-request
 * scope is opened; every call sees the same binding instances.
 */
class StaticUrpcBindingResolver(
    serviceFunctions: List<ServiceFunctionBinding<*>> = emptyList(),
    streamingServiceFunctions: List<StreamingServiceFunctionBinding<*>> = emptyList(),
    bidirectionalStreamingServiceFunctions: List<BidirectionalStreamingServiceFunctionBinding<*>> = emptyList(),
) : UrpcBindingResolver {
    private val byName: Map<String, ServiceFunctionBinding<*>> =
        serviceFunctions.associateBy { it.name }
    private val streamingByName: Map<String, StreamingServiceFunctionBinding<*>> =
        streamingServiceFunctions.associateBy { it.name }
    private val bidirectionalByName: Map<String, BidirectionalStreamingServiceFunctionBinding<*>> =
        bidirectionalStreamingServiceFunctions.associateBy { it.name }

    override suspend fun withServiceFunction(
        call: ApplicationCall,
        name: String,
        block: suspend (ServiceFunctionBinding<*>?) -> Unit,
    ) {
        block(byName[name])
    }

    override suspend fun withStreamingServiceFunction(
        socketSession: WebSocketServerSession,
        name: String,
        block: suspend (
            unidirectional: StreamingServiceFunctionBinding<*>?,
            bidirectional: BidirectionalStreamingServiceFunctionBinding<*>?,
        ) -> Unit,
    ) {
        block(streamingByName[name], bidirectionalByName[name])
    }
}

/**
 * Registers the urpc HTTP and WebSocket routes on the receiver.
 *
 * Mounts:
 *  - `POST /services/{service-function-name}` for unary [ServiceFunction] calls
 *  - `WS   /streamingServices/{streaming-function-name}` for both
 *    [StreamingServiceFunctionBinding] and
 *    [BidirectionalStreamingServiceFunctionBinding] calls
 *
 * The host is responsible for installing the Ktor `WebSockets` plugin before calling
 * this. Pass a [UrpcBindingResolver] (typically a [StaticUrpcBindingResolver] or a
 * DI-backed implementation) to determine which binding answers each call.
 */
fun Route.urpc(
    resolver: UrpcBindingResolver,
    logger: UrpcLogger = UrpcLogger.NoOp,
) {
    post("/services/{service-function-name}") {
        val name = call.parameters["service-function-name"]
            ?: return@post
        resolver.withServiceFunction(call, name) { binding ->
            binding?.handle(call)
        }
    }

    webSocket("/streamingServices/{streaming-function-name}") {
        val name = call.parameters["streaming-function-name"]
            ?: return@webSocket
        resolver.withStreamingServiceFunction(this, name) { unidirectional, bidirectional ->
            try {
                when {
                    unidirectional != null -> unidirectional.handle(this)
                    bidirectional != null -> bidirectional.handle(this)
                }
            } catch (e: CancellationException) {
                // Normal websocket lifecycle — client disconnected
            } catch (e: ClosedReceiveChannelException) {
                // Normal websocket lifecycle — client disconnected
            } catch (t: Throwable) {
                logger.error("Streaming service error for $name", t)
            }
        }
    }
}

// TODO(urpc): consider providing a small Application-level installer that also installs
// the Ktor `WebSockets` and `ContentNegotiation` plugins with sensible defaults, e.g.
// `Application.installUrpc(resolver, configureWebSockets = { ... })`. Left out of this
// initial cut to avoid forcing a particular configuration on consumers.
