package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.urpc.BidirectionalStreamingServiceFunction
import dev.isaacudy.udytils.urpc.ServiceFunction
import dev.isaacudy.udytils.urpc.StreamingServiceFunction
import dev.isaacudy.udytils.urpc.UrpcLogger
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.serializer

/**
 * Builds typed callers for [ServiceFunction], [StreamingServiceFunction] and
 * [BidirectionalStreamingServiceFunction] definitions.
 *
 * Usage:
 * ```
 * val sendMessage: ChatService.SendMessage = clientFactory.create(::SendMessage)
 * val response = sendMessage(SendMessage.Request(...))
 * ```
 *
 * @param httpClient the Ktor [HttpClient] used for HTTP and WebSocket calls. Must
 *  have the WebSockets plugin installed.
 * @param baseUrl the protocol + host + base path of the server (e.g. "https://api.example.com").
 * @param authTokenProvider returns the bearer token to include on each request, or
 *  null for unauthenticated calls.
 * @param tokenRefresher invoked when an HTTP call returns 401; the call is retried
 *  once after the refresher completes.
 * @param logger optional [UrpcLogger] used for streaming reconnect / error messages.
 */
class ServiceClientFactory(
    @PublishedApi internal val httpClient: HttpClient,
    @PublishedApi internal val baseUrl: String,
    @PublishedApi internal val authTokenProvider: () -> String? = { null },
    @PublishedApi internal val tokenRefresher: suspend () -> Unit = {},
    @PublishedApi internal val logger: UrpcLogger = UrpcLogger.NoOp,
) {
    /**
     * Creates a typed [ServiceFunction] caller. The wire path is derived from the
     * runtime qualified name of [Func]. See the TODO in `ServiceFunction.kt`.
     */
    inline fun <reified Func : ServiceFunction<Req, Res>, reified Req : Any, reified Res : Any> create(
        constructor: (suspend (Req) -> Res) -> Func
    ): Func {
        val caller = ServiceFunctionCaller(
            httpClient = httpClient,
            baseUrl = baseUrl,
            path = Func::class.qualifiedName!!,
            requestSerializer = serializer<Req>(),
            responseSerializer = serializer<Res>(),
            isUnitRequest = Req::class == Unit::class,
            isUnitResponse = Res::class == Unit::class,
            authTokenProvider = authTokenProvider,
            tokenRefresher = tokenRefresher,
        )
        return constructor { caller.call(it) }
    }

    /**
     * Creates a typed [StreamingServiceFunction] caller backed by an auto-reconnecting
     * WebSocket.
     */
    inline fun <reified Func : StreamingServiceFunction<Req, Res>, reified Req, reified Res> create(
        constructor: ((Req) -> Flow<Res>) -> Func
    ): Func {
        val caller = StreamingServiceFunctionCaller(
            httpClient = httpClient,
            baseUrl = baseUrl,
            path = Func::class.qualifiedName!!,
            requestSerializer = serializer<Req>(),
            responseSerializer = serializer<Res>(),
            isUnitRequest = Req::class == Unit::class,
            authTokenProvider = authTokenProvider,
            logger = logger,
        )
        return constructor { caller.stream(it) }
    }

    /**
     * Creates a typed [BidirectionalStreamingServiceFunction] caller backed by an
     * auto-reconnecting WebSocket. On reconnect, the latest value from the request
     * flow is replayed so the server resumes from the correct state.
     */
    inline fun <reified Func : BidirectionalStreamingServiceFunction<Req, Res>, reified Req, reified Res> create(
        constructor: ((Flow<Req>) -> Flow<Res>) -> Func
    ): Func {
        val caller = BidirectionalStreamingServiceFunctionCaller(
            httpClient = httpClient,
            baseUrl = baseUrl,
            path = Func::class.qualifiedName!!,
            requestSerializer = serializer<Req>(),
            responseSerializer = serializer<Res>(),
            authTokenProvider = authTokenProvider,
            logger = logger,
        )
        return constructor { caller.stream(it) }
    }
}
