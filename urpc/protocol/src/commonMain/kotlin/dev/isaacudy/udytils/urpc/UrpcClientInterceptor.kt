package dev.isaacudy.udytils.urpc

/**
 * Client-side interceptor, run in order before each call is opened. Use it to attach
 * per-call metadata — auth tokens, trace ids, feature flags — by mutating
 * [UrpcCallContext.metadata]; the resulting metadata travels in the call's
 * [UrpcFrame.Open] frame.
 *
 * Interceptors are transport-agnostic: they see the call context, not the socket, so the
 * same chain composes whether calls are multiplexed over one connection or not.
 */
fun interface UrpcClientInterceptor {
    suspend fun interceptOpen(context: UrpcCallContext)
}
