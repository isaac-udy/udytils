package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcFrame
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcServerCall
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/**
 * A streaming or bidirectional urpc call multiplexed over the shared `/urpc` WebSocket.
 *
 * The request arrives in the [open] frame ([UrpcFrame.Open.payload]); for bidirectional calls,
 * subsequent request items arrive on [requests] (demultiplexed from the socket by the route).
 * Every response frame is tagged with this call's [UrpcFrame.callId] and written through [send],
 * which serialises writes across all calls sharing the socket. One call's terminal `Complete`/
 * `Error` ends only that call — the connection stays up for the others.
 */
internal class MuxUrpcServerCall(
    private val open: UrpcFrame.Open,
    override val applicationCall: ApplicationCall,
    private val requests: ReceiveChannel<JsonElement>,
    private val send: suspend (UrpcFrame) -> Unit,
    private val errorMapper: ServiceErrorMapper,
    private val logger: UrpcLogger,
) : UrpcServerCall, KtorServerCall {

    override val wireName: String get() = open.wireName
    private val callId: Long get() = open.callId

    override suspend fun <Req, Res> handleUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        invoke: suspend (Req) -> Res,
    ) {
        error(
            "Unary function '${descriptor.name}' was invoked on the /urpc WebSocket; " +
                "unary calls use HTTP POST /services/{name}.",
        )
    }

    override suspend fun <Req, Res> handleStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        invoke: (Req) -> Flow<Res>,
    ) {
        try {
            @Suppress("UNCHECKED_CAST")
            val request: Req = if (descriptor.isUnitRequest) {
                Unit as Req
            } else {
                serviceFunctionJson.decodeFromJsonElement(descriptor.requestSerializer, requirePayload())
            }
            invoke(request).collect { send(UrpcFrame.Data(callId, encode(descriptor.responseSerializer, it))) }
            send(UrpcFrame.Complete(callId))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            sendError(t, descriptor.name)
        }
    }

    override suspend fun <Req, Res> handleBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        invoke: (Flow<Req>) -> Flow<Res>,
    ) {
        try {
            val requestFlow = requests.consumeAsFlow()
                .map { serviceFunctionJson.decodeFromJsonElement(descriptor.requestSerializer, it) }
            invoke(requestFlow).collect { send(UrpcFrame.Data(callId, encode(descriptor.responseSerializer, it))) }
            send(UrpcFrame.Complete(callId))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            sendError(t, descriptor.name)
        }
    }

    private fun requirePayload(): JsonElement =
        open.payload ?: error("Open frame for '${open.wireName}' is missing its request payload")

    private fun <Res> encode(serializer: KSerializer<Res>, value: Res): JsonElement =
        serviceFunctionJson.encodeToJsonElement(serializer, value)

    private suspend fun sendError(t: Throwable, name: String) {
        val status = errorMapper.mapStatus(t)
        if (status.value >= 500) {
            logger.error("Streaming error on $name", t)
        } else {
            logger.debug("Streaming error on $name: ${status.value} ${t::class.simpleName}: ${t.message}")
        }
        // This runs inside an already-entered catch; if the socket is gone the error frame can't be
        // delivered. Swallow that (re-raising only true cancellation) so the failed error-report
        // can't escape and tear down the shared connection / sibling calls.
        try {
            send(UrpcFrame.Error(callId, ServiceError.from(t), status.value))
        } catch (e: CancellationException) {
            throw e
        } catch (sendFailure: Throwable) {
            logger.debug("urpc server: failed to deliver Error for $name on call $callId: ${sendFailure.message}")
        }
    }
}
