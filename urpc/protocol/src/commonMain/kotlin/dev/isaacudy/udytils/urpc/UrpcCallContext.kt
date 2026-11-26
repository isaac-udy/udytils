package dev.isaacudy.udytils.urpc

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Mutable per-call context threaded through the interceptor chain.
 *
 * [metadata] is the per-call "header" bag: on the client, interceptors write into it and it is
 * sent in the call's [UrpcFrame.Open] frame (for streaming/bidirectional) or as HTTP request
 * headers (for unary); on the server it is the metadata read back from that call, which server
 * interceptors read (e.g. to authenticate or trace).
 *
 * [kind] lets an interceptor behave differently per call shape — e.g. gate (suspend) a streaming
 * call until authenticated, but never gate a [UrpcCallKind.UNARY] call (so an unauthenticated
 * call such as login still proceeds).
 *
 * It is transport-agnostic on purpose — interceptors operate on this context, not on the wire —
 * so the same interceptor chain works whether or not calls are multiplexed.
 *
 * [callerContext] is the [dev.isaacudy.udytils.coroutines.LaunchContext] of the coroutine that made
 * the call, captured once when the call starts. The client runs the interceptors again for each
 * re-open of a streaming call, in its connection's coroutine; [callerContext] stays the caller's,
 * so an interceptor reads per-caller values such as a trace span from [callerContext].
 */
class UrpcCallContext(
    val wireName: String,
    val kind: UrpcCallKind,
    val metadata: MutableMap<String, String> = mutableMapOf(),
    val callerContext: CoroutineContext = EmptyCoroutineContext,
)

/** The shape of the call an interceptor is being invoked for. */
enum class UrpcCallKind {
    UNARY,
    SERVER_STREAMING,
    BIDIRECTIONAL,
}
