package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcServerCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket

/**
 * Registers the urpc catch-all routes under [rootPath] and invokes [handler]
 * for every incoming call.
 *
 * The handler receives a [UrpcServerCall] and is responsible for finding the
 * right [UrpcService] for the call and invoking [UrpcService.handle] on it.
 * The typical Koin-backed shape:
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
 * Or with a static list (e.g. for tests):
 *
 * ```
 * val services = listOf<UrpcService>(ChatServiceUrpcBinding(impl), ...)
 * routing {
 *     urpc { call ->
 *         services.firstOrNull { it.accepts(call) }?.handle(call)
 *             ?: call.applicationCall.respond(HttpStatusCode.NotFound)
 *     }
 * }
 * ```
 *
 * Two routes are registered:
 * - `POST ${rootPath}/services/{wireName}` for unary calls
 * - `WS   ${rootPath}/streamingServices/{wireName}` for streaming and bidi calls
 *
 * The host is responsible for installing Ktor's `WebSockets` plugin if any
 * streaming services will be served.
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

private fun registerUrpcRoutes(
    route: Route,
    errorMapper: ServiceErrorMapper,
    logger: UrpcLogger,
    handler: suspend (UrpcServerCall) -> Unit,
) {
    route.post("/services/{wireName}") {
        val wireName = call.parameters["wireName"] ?: return@post
        val urpcCall = KtorUrpcServerCall(
            wireName = wireName,
            applicationCall = call,
            webSocketSession = null,
            errorMapper = errorMapper,
            logger = logger,
        )
        handler(urpcCall)
    }
    route.webSocket("/streamingServices/{wireName}") {
        val wireName = call.parameters["wireName"] ?: return@webSocket
        val urpcCall = KtorUrpcServerCall(
            wireName = wireName,
            applicationCall = call,
            webSocketSession = this,
            errorMapper = errorMapper,
            logger = logger,
        )
        handler(urpcCall)
    }
}
