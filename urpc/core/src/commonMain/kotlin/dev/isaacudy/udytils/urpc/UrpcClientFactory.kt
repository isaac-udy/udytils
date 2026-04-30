package dev.isaacudy.udytils.urpc

import kotlinx.coroutines.flow.Flow

/**
 * Transport-agnostic interface that a urpc client implementation provides.
 *
 * Generated `${ServiceName}UrpcClient` classes (emitted by `:urpc:urpc-processor`)
 * delegate every call into [callUnary] / [callStreaming] / [callBidirectional],
 * which means the generated code only has to depend on `:urpc:urpc-core` — not on
 * any specific transport implementation.
 *
 * The Ktor-backed implementation is constructed via
 * `httpClient.urpcClient(baseUrl, ...)` from `:urpc:urpc-client`.
 */
interface UrpcClientFactory {
    suspend fun <Req, Res> callUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
    ): Res

    fun <Req, Res> callStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        request: Req,
    ): Flow<Res>

    fun <Req, Res> callBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        requests: Flow<Req>,
    ): Flow<Res>
}
