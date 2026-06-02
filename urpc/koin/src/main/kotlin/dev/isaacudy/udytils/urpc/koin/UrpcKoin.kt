package dev.isaacudy.udytils.urpc.koin

import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcServerCall
import dev.isaacudy.udytils.urpc.UrpcService
import dev.isaacudy.udytils.urpc.server.ServiceErrorMapper
import dev.isaacudy.udytils.urpc.server.applicationCall
import dev.isaacudy.udytils.urpc.server.urpc
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.core.qualifier.TypeQualifier
import org.koin.ktor.ext.getKoin
import java.util.concurrent.atomic.AtomicLong

/**
 * Koin scope qualifier for a single urpc call.
 *
 * [urpcWithKoin] opens exactly one `UrpcCall`-qualified Koin scope per call — one
 * per unary HTTP request, and **one per streaming/bidirectional `Open`** — and
 * closes it when that call ends (completes, errors, or is cancelled).
 *
 * This is the key difference from Koin's Ktor request-scope plugin: that scope is
 * tied to the *HTTP request* and does **not** survive the WebSocket upgrade, so
 * streaming calls (which all share one upgraded socket) cannot be per-call scoped
 * with it. `UrpcCall` is driven by the urpc *call* lifecycle instead, so streaming
 * and unary calls get genuine, symmetric per-call scoping.
 *
 * Register a feature's per-call services under it and resolve everything they need
 * — including auth derived from [UrpcServerCall.metadata] — from the same scope:
 *
 * ```
 * val chatModule = module {
 *     scope<UrpcCall> {
 *         scoped {
 *             val token = get<UrpcServerCall>().metadata["Authorization"]?.removePrefix("Bearer ")
 *             SessionAuth(token, verify = get<AuthService>()::verify)
 *         }
 *         scopedOf(::ChatServiceImpl) bind ChatService::class
 *         scoped<UrpcService> { ChatServiceUrpcBinding(get()) }
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
 * The call's [UrpcServerCall] and the underlying Ktor [ApplicationCall] are declared
 * into the scope, so `get<UrpcServerCall>()` and `get<ApplicationCall>()` resolve
 * inside any `scoped { ... }` definition. `UrpcServerCall.metadata` carries per-call
 * auth/trace headers uniformly — request headers for unary, `Open`-frame metadata
 * for streaming — so a single `SessionAuth` definition serves both.
 */
public class UrpcCall internal constructor()

/** Process-unique ids for the per-call [UrpcCall] scopes opened by [urpcWithKoin]. */
private val urpcCallScopeIds = AtomicLong(0)

/**
 * Mounts the urpc routes and dispatches every call to whichever [UrpcService] is
 * registered for it.
 *
 * For each call a fresh [UrpcCall] Koin scope is opened (see [UrpcCall]), the
 * call's [UrpcServerCall] and underlying [ApplicationCall] are declared into it,
 * and the scope is closed once the call finishes — including when a streaming call
 * ends, errors, or is cancelled.
 *
 * The accepting service is looked up in both:
 *  - the per-call [UrpcCall] scope — `scope<UrpcCall> { scoped<UrpcService> { ... } }`
 *    bindings, the usual case once per-call dependencies (auth, request info) matter; and
 *  - the application Koin — `single<UrpcService> { ... }` bindings, for stateless
 *    services that need no per-call scope.
 *
 * If no service accepts the call, responds `404 Not Found`. Use [Route.urpc]
 * directly if you want different fallback behaviour.
 */
fun Route.urpcWithKoin(
    rootPath: String = "",
    errorMapper: ServiceErrorMapper = ServiceErrorMapper.Default,
    logger: UrpcLogger = UrpcLogger.NoOp,
) {
    urpc(rootPath = rootPath, errorMapper = errorMapper, logger = logger) { call ->
        val ktorCall = call.applicationCall
        val koin = ktorCall.application.getKoin()
        val scope = koin.createScope(
            "urpc-call-${urpcCallScopeIds.incrementAndGet()}",
            TypeQualifier(UrpcCall::class),
        )
        try {
            // Per-call dependencies resolve these: SessionAuth from UrpcServerCall.metadata,
            // request/connection info from the ApplicationCall.
            scope.declare<UrpcServerCall>(call)
            scope.declare<ApplicationCall>(ktorCall)

            // Scoped bindings live under UrpcCall; application singletons under root Koin.
            // runCatching guards the (rare) misconfiguration where neither is present.
            val scopedServices = runCatching { scope.getAll<UrpcService>() }.getOrNull().orEmpty()
            val rootServices = runCatching { koin.getAll<UrpcService>() }.getOrNull().orEmpty()
            val service = (scopedServices + rootServices).firstOrNull { it.accepts(call) }
            if (service == null) {
                ktorCall.respond(HttpStatusCode.NotFound)
                return@urpc
            }
            service.handle(call)
        } finally {
            scope.close()
        }
    }
}
