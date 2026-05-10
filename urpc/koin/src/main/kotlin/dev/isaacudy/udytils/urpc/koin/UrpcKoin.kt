package dev.isaacudy.udytils.urpc.koin

import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcService
import dev.isaacudy.udytils.urpc.server.ServiceErrorMapper
import dev.isaacudy.udytils.urpc.server.applicationCall
import dev.isaacudy.udytils.urpc.server.urpc
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.scope

/**
 * Mounts the urpc routes and dispatches every call to whichever [UrpcService]
 * is registered in the per-call Koin scope's `getAll<UrpcService>()`.
 *
 * Pairs with Koin's Ktor request-scope plugin — the scope is opened
 * automatically per HTTP request and closed when the response completes. Bind
 * each generated `XServiceUrpcBinding` inside a `requestScope { ... }` block in
 * your Koin module so it's resolved fresh per call.
 *
 * ```
 * val chatModule = module {
 *     requestScope {
 *         scopedOf(::ChatServiceImpl) bind ChatService::class
 *         scopedOf(::ChatServiceUrpcBinding) bind UrpcService::class
 *     }
 * }
 *
 * fun Application.module() {
 *     install(Koin) { modules(chatModule) }
 *     install(WebSockets)
 *     routing { urpcWithKoin() }
 * }
 * ```
 *
 * If no service in the current scope accepts the call, the handler responds
 * with `404 Not Found`. Use [Route.urpc] directly if you want different
 * fallback behaviour.
 *
 * **Streaming caveat** — Koin's request scope is tied to the HTTP request
 * lifecycle, which does NOT survive WebSocket upgrades. We verified this
 * empirically in `ExampleServiceWithKoinTest` (commit context): unary
 * services bound inside `scope<RequestScope> { ... }` work fine, streaming
 * services do not. For streaming and bidirectional services, bind the
 * `UrpcService` at the application level instead:
 *
 * ```
 * module {
 *     single<ChatService> { ChatServiceImpl() }
 *     single<UrpcService> { ChatServiceUrpcBinding(get()) }   // application-singleton
 *
 *     scope<RequestScope> {
 *         scoped<RequestUserService> { /* per-call deps */ }
 *         scoped<UrpcService> { UserServiceUrpcBinding(get()) }   // unary only
 *     }
 * }
 * ```
 *
 * Both `single` and `scope<RequestScope>` bindings are checked on every call;
 * the application-level singletons cover the WS path, the request-scoped
 * ones cover the unary path.
 */
fun Route.urpcWithKoin(
    rootPath: String = "",
    errorMapper: ServiceErrorMapper = ServiceErrorMapper.Default,
    logger: UrpcLogger = UrpcLogger.NoOp,
) {
    urpc(rootPath = rootPath, errorMapper = errorMapper, logger = logger) { call ->
        val ktorCall = call.applicationCall
        // Prefer the per-request scope when the Koin Ktor plugin is configured;
        // fall back to the application-level Koin so the helper is useful even
        // without `requestScope { ... }` bindings.
        val perCallServices = runCatching { ktorCall.scope.getAll<UrpcService>() }.getOrNull().orEmpty()
        val rootServices = ktorCall.application.getKoin().getAll<UrpcService>()
        val service = (perCallServices + rootServices).firstOrNull { it.accepts(call) }
        if (service == null) {
            ktorCall.respond(HttpStatusCode.NotFound)
            return@urpc
        }
        service.handle(call)
    }
}
