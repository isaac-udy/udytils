package dev.isaacudy.udytils.urpc

import kotlinx.coroutines.flow.Flow

/**
 * The server-side view of an incoming urpc call. Carries the wire name being
 * called and exposes the transport-aware `handle*` methods that the generated
 * service bindings dispatch into.
 *
 * Transport-specific implementations (e.g. [`KtorUrpcServerCall`][dev.isaacudy.udytils.urpc.server]
 * in `:urpc:server`) own the actual decode/invoke/encode mechanics; this
 * interface lets generated `XServiceUrpcBinding` code depend only on
 * `:urpc:protocol` regardless of which transport the host eventually wires up.
 */
interface UrpcServerCall {
    /**
     * Wire name of the call, e.g. `"chat.sendMessage"`. The generated binding
     * uses this to dispatch to the matching service method.
     */
    val wireName: String

    /**
     * Decodes the request body, invokes [invoke] with it, encodes the response,
     * and writes it back to the caller. Errors thrown by [invoke] are mapped
     * to a [ServiceError] response.
     */
    suspend fun <Req, Res> handleUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        invoke: suspend (Req) -> Res,
    )

    /**
     * Decodes the request, invokes [invoke], and streams the resulting [Flow]
     * back to the caller as a sequence of [UrpcStreamingFrame.Data] frames
     * followed by a single [UrpcStreamingFrame.Complete] when the flow ends.
     */
    suspend fun <Req, Res> handleStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        invoke: (Req) -> Flow<Res>,
    )

    /**
     * Bridges incoming request frames into the [Flow<Req>] passed to [invoke],
     * and streams the resulting [Flow<Res>] back as [UrpcStreamingFrame.Data]
     * frames followed by a [UrpcStreamingFrame.Complete] frame.
     */
    suspend fun <Req, Res> handleBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        invoke: (Flow<Req>) -> Flow<Res>,
    )
}
