package dev.isaacudy.udytils.urpc

import kotlinx.coroutines.flow.Flow

// TODO(urpc): wire identity is currently derived from `Func::class.qualifiedName`
// at the call/binding sites. That makes renaming or moving a service function a
// silent breaking change for any deployed client, and rolling deploys can break
// when client and server disagree on the qualified name. We should add a way to
// override the wire name explicitly, e.g. an optional `val name: String` on the
// marker (with a default that falls back to qualifiedName), or a separate
// `ServiceFunctionNameResolver` interface threaded through the client/server.
// Decision: pick the approach (interface property vs. resolver) before the first
// non-trivial consumer ships.

/**
 * Marker interface for a service function definition. Each service method is defined
 * as an `object` extending this type with nested `Request` and `Response` classes.
 *
 * Use [Unit] for either type parameter when no input or output is needed.
 *
 * Example:
 * ```
 * object ChatServiceFunctions {
 *     object SendMessage : ServiceFunction<SendMessage.Request, SendMessage.Response> {
 *         @Serializable data class Request(val message: String)
 *         @Serializable data class Response(val reply: String)
 *     }
 *     object DeleteMessage : ServiceFunction<DeleteMessage.Request, Unit> {
 *         @Serializable data class Request(val id: String)
 *     }
 * }
 * ```
 */
fun interface ServiceFunction<Request : Any, Response : Any> {
    suspend operator fun invoke(request: Request): Response

    companion object
}

/**
 * Marker interface for a streaming service function definition.
 * Use [Unit] for [Request] when no input is needed.
 */
interface StreamingServiceFunction<Request, Response> {
    operator fun invoke(request: Request): Flow<Response>

    companion object
}

/**
 * Marker interface for a bidirectional streaming service function definition.
 * Both client and server exchange messages over a shared WebSocket connection.
 *
 * The client sends a [Flow] of requests (e.g. pagination state, search queries)
 * and receives a [Flow] of responses that updates in real-time as the request changes.
 *
 * Server implementations typically use `flatMapLatest` on the incoming request flow
 * to switch data sources when the client updates its request.
 */
interface BidirectionalStreamingServiceFunction<Request, Response> {
    operator fun invoke(requests: Flow<Request>): Flow<Response>

    companion object
}
