package dev.isaacudy.udytils.urpc

/**
 * Server-side interceptor, run in order for each incoming call before it is dispatched to
 * the service implementation. Read [UrpcCallContext.metadata] for the per-call headers the
 * client attached — to authenticate, authorise, log, or meter.
 *
 * Throwing from here rejects the call: the framework turns the exception into an error
 * response via the configured ServiceErrorMapper (e.g. throw `UnauthorizedException` → 401).
 */
fun interface UrpcServerInterceptor {
    suspend fun interceptCall(context: UrpcCallContext)
}
