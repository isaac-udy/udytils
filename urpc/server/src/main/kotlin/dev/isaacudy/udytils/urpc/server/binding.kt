package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.BidirectionalStreamingServiceFunction
import dev.isaacudy.udytils.urpc.ServiceFunction
import dev.isaacudy.udytils.urpc.StreamingServiceFunction
import dev.isaacudy.udytils.urpc.UrpcLogger
import kotlinx.serialization.serializer

/**
 * Wraps a [ServiceFunction] implementation as an HTTP [ServiceFunctionBinding].
 *
 * ```
 * class ChatServiceImpl(private val sessionAuth: SessionAuth) {
 *     val sendMessage = SendMessage { request ->
 *         val userId = sessionAuth.requireUser().first()
 *         SendMessage.Response(...)
 *     }.binding()
 * }
 * ```
 */
inline fun <reified Func : ServiceFunction<Request, Response>, reified Request : Any, reified Response : Any> Func.binding(
    errorMapper: ServiceErrorMapper = ServiceErrorMapper.Default,
): ServiceFunctionBinding<Func> {
    return HttpServiceFunctionBinding(
        name = Func::class.qualifiedName ?: "unknown",
        requestSerializer = serializer<Request>(),
        responseSerializer = serializer<Response>(),
        isUnitRequest = Request::class == Unit::class,
        isUnitResponse = Response::class == Unit::class,
        errorMapper = errorMapper,
        handler = this::invoke,
    )
}

/**
 * Wraps a [StreamingServiceFunction] implementation as a [StreamingServiceFunctionBinding].
 */
inline fun <reified Func : StreamingServiceFunction<Request, Response>, reified Request : Any, reified Response : Any> Func.binding(
    logger: UrpcLogger = UrpcLogger.NoOp,
): StreamingServiceFunctionBinding<Func> {
    return WebSocketServiceFunctionBinding(
        name = Func::class.qualifiedName ?: "unknown",
        requestSerializer = serializer<Request>(),
        responseSerializer = serializer<Response>(),
        isUnitRequest = Request::class == Unit::class,
        logger = logger,
        handler = this::invoke,
    )
}

/**
 * Wraps a [BidirectionalStreamingServiceFunction] implementation as a
 * [BidirectionalStreamingServiceFunctionBinding].
 */
inline fun <reified Func : BidirectionalStreamingServiceFunction<Request, Response>, reified Request : Any, reified Response : Any> Func.binding(
    logger: UrpcLogger = UrpcLogger.NoOp,
): BidirectionalStreamingServiceFunctionBinding<Func> {
    return BidirectionalWebSocketServiceFunctionBinding(
        name = Func::class.qualifiedName ?: "unknown",
        requestSerializer = serializer<Request>(),
        responseSerializer = serializer<Response>(),
        logger = logger,
        handler = this::invoke,
    )
}

// TODO(urpc): the original arcane-archivist code shipped Koin extensions
// (`KoinDefinition.registerServiceFunction(KProperty1)` and `Scope.getServiceClient(...)`)
// to bind/discover service implementations through Koin. Those have been deliberately
// left out of urpc-server / urpc-client to keep the libraries DI-agnostic. If we end up
// wanting them, ship them as a separate `:urpc:koin` artifact rather than reintroducing
// the Koin dependency here.
