package dev.isaacudy.udytils.urpc

/**
 * Mutable per-call context threaded through the interceptor chain.
 *
 * [metadata] is the per-call "header" bag: on the client, interceptors write into it and
 * it is sent in the call's [UrpcFrame.Open] frame; on the server it is the metadata
 * decoded from that frame, which server interceptors read (e.g. to authenticate or trace).
 *
 * It is transport-agnostic on purpose — interceptors operate on this context, not on the
 * wire — so the same interceptor chain works whether or not calls are multiplexed.
 */
class UrpcCallContext(
    val wireName: String,
    val metadata: MutableMap<String, String> = mutableMapOf(),
)
