package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcServerCall
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.coroutines.flow.Flow

/** A urpc server call backed by Ktor; exposes the underlying [ApplicationCall]. */
internal interface KtorServerCall {
    val applicationCall: ApplicationCall
}

/**
 * A unary urpc call backed by an HTTP request. Streaming and bidirectional functions are served
 * over the multiplexed `/urpc` WebSocket (see [MuxUrpcServerCall]), so they never land here.
 */
internal class KtorUrpcServerCall(
    override val wireName: String,
    override val applicationCall: ApplicationCall,
    private val errorMapper: ServiceErrorMapper,
    private val logger: UrpcLogger,
) : UrpcServerCall, KtorServerCall {

    override val metadata: Map<String, String>
        get() = applicationCall.request.headers.entries().associate { it.key to it.value.first() }

    override suspend fun <Req, Res> handleUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        invoke: suspend (Req) -> Res,
    ) {
        try {
            @Suppress("UNCHECKED_CAST")
            val request: Req = if (descriptor.isUnitRequest) {
                Unit as Req
            } else {
                serviceFunctionJson.decodeFromString(descriptor.requestSerializer, applicationCall.receive<String>())
            }
            val response = invoke(request)
            if (descriptor.isUnitResponse) {
                applicationCall.respond(HttpStatusCode.NoContent)
            } else {
                applicationCall.respond(
                    HttpStatusCode.OK,
                    serviceFunctionJson.encodeToString(descriptor.responseSerializer, response),
                )
            }
        } catch (t: Throwable) {
            handleServiceError(applicationCall, t, errorMapper, logger)
        }
    }

    override suspend fun <Req, Res> handleStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        invoke: (Req) -> Flow<Res>,
    ) {
        error(
            "Streaming function '${descriptor.name}' was invoked on the unary HTTP route; " +
                "streaming calls are served over the /urpc WebSocket.",
        )
    }

    override suspend fun <Req, Res> handleBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        invoke: (Flow<Req>) -> Flow<Res>,
    ) {
        error(
            "Bidirectional function '${descriptor.name}' was invoked on the unary HTTP route; " +
                "streaming calls are served over the /urpc WebSocket.",
        )
    }
}

/**
 * Recovers the underlying Ktor [ApplicationCall] from a [UrpcServerCall] — the HTTP request for a
 * unary call, or the WebSocket connection's call for a streaming/bidirectional call.
 */
val UrpcServerCall.applicationCall: ApplicationCall
    get() = (this as KtorServerCall).applicationCall
