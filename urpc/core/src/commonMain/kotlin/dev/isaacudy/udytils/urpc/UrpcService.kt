package dev.isaacudy.udytils.urpc

/**
 * A server-side service handler for one or more urpc functions.
 *
 * The KSP processor generates a concrete `XServiceUrpcBinding` per `@Urpc`
 * interface — bind those in your DI graph (one per service interface) and the
 * user's `urpc { call -> ... }` lambda looks the matching one up per call and
 * invokes [handle].
 *
 * ```
 * routing {
 *     urpc { call ->
 *         val service = call.applicationCall.scope
 *             .getAll<UrpcService>()
 *             .firstOrNull { it.accepts(call) }
 *             ?: return@urpc call.applicationCall.respond(HttpStatusCode.NotFound)
 *         service.handle(call)
 *     }
 * }
 * ```
 */
interface UrpcService {
    /**
     * Returns true iff this service can handle the given [call]. Generated
     * bindings implement this by checking [UrpcServerCall.wireName] against the
     * set of wire names emitted from the service interface.
     */
    fun accepts(call: UrpcServerCall): Boolean

    /**
     * Dispatches [call] to the matching service method. Implementations
     * delegate to [UrpcServerCall.handleUnary] / [handleStreaming] /
     * [handleBidirectional] based on the function shape.
     */
    suspend fun handle(call: UrpcServerCall)
}
