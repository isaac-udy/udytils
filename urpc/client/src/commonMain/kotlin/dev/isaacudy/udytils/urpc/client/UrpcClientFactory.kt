package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcLogger
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow

/**
 * Builds typed urpc service clients.
 *
 * The primary entry point is the generated `create(KClass<ServiceX>): ServiceX`
 * extension function, emitted by `:urpc:urpc-processor` in the same package as the
 * `@UrpcService`-annotated interface:
 *
 * ```
 * import com.example.chat.create   // generated alongside ChatService
 *
 * val chat: ChatService = clientFactory.create(ChatService::class)
 * val response = chat.sendMessage(SendRequest(...))
 * ```
 *
 * Generated client implementations dispatch each function through the
 * `callUnary` / `callStreaming` / `callBidirectional` methods on this class.
 *
 * @param httpClient the Ktor [HttpClient] used for HTTP and WebSocket calls. Must
 *  have the WebSockets plugin installed if any streaming services are consumed.
 * @param baseUrl the protocol + host + base path of the server (e.g. "https://api.example.com").
 * @param authTokenProvider returns the bearer token to include on each request, or
 *  null for unauthenticated calls.
 * @param tokenRefresher invoked when an HTTP call returns 401; the call is retried
 *  once after the refresher completes.
 * @param logger optional [UrpcLogger] used for streaming reconnect / error messages.
 */
class UrpcClientFactory(
    internal val httpClient: HttpClient,
    internal val baseUrl: String,
    internal val authTokenProvider: () -> String? = { null },
    internal val tokenRefresher: suspend () -> Unit = {},
    internal val logger: UrpcLogger = UrpcLogger.NoOp,
) {
    /**
     * Generated `_createService` overloads dispatch into this method to issue a
     * unary HTTP call.
     */
    suspend fun <Req, Res> callUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
    ): Res = unaryCaller(descriptor).call(request)

    /**
     * Generated streaming overloads dispatch into this method.
     */
    fun <Req, Res> callStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        request: Req,
    ): Flow<Res> = streamingCaller(descriptor).stream(request)

    /**
     * Generated bidirectional overloads dispatch into this method.
     */
    fun <Req, Res> callBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        requests: Flow<Req>,
    ): Flow<Res> = bidirectionalCaller(descriptor).stream(requests)

    private fun <Req, Res> unaryCaller(descriptor: ServiceDescriptor<Req, Res>) =
        UnaryCaller(this, descriptor)

    private fun <Req, Res> streamingCaller(descriptor: StreamingServiceDescriptor<Req, Res>) =
        StreamingCaller(this, descriptor)

    private fun <Req, Res> bidirectionalCaller(descriptor: BidirectionalServiceDescriptor<Req, Res>) =
        BidirectionalCaller(this, descriptor)
}

// NOTE: there is intentionally NO `fun <T : Any> UrpcClientFactory.create(type: KClass<T>)`
// fallback. KSP emits a `create(type: KClass<ServiceX>): ServiceX` extension in the same
// package as each `@UrpcService` interface; users `import` it from their service's
// package and the call resolves to the generated overload at compile time.
//
// A generic fallback would shadow that resolution: if it is `import`ed at the call site,
// Kotlin prefers the imported function over the same-package implicit one and silently
// dispatches every call to the fallback (which can only error). Better to surface
// missing wiring as `unresolved reference: create` at compile time.
//
// TODO(urpc): consider whether to also ship a runtime-registry-backed
// `factory.create<T>()` inline reified shorthand. Compile-time overload resolution
// can't dispatch through an inline reified call to a specific overload, so a reified
// entry point would require a runtime registry populated by generated code at
// class-load time. Mild ergonomic win, real complexity. Skip for now.
