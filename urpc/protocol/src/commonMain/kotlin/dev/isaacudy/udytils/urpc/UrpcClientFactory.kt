package dev.isaacudy.udytils.urpc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport-agnostic interface that a urpc client implementation provides.
 *
 * Generated `${ServiceName}UrpcClient` classes (emitted by `:urpc:processor`)
 * delegate every call into [callUnary] / [callStreaming] / [callBidirectional],
 * which means the generated code only has to depend on `:urpc:protocol` — not on
 * any specific transport implementation.
 *
 * The Ktor-backed implementation is constructed via
 * `httpClient.urpcClient(baseUrl, ...)` from `:urpc:client`.
 *
 * [close] stops the factory's background work (the streaming connection and its reconnect loop);
 * calls made afterwards fail. It does not close the underlying transport, such as an `HttpClient`.
 */
interface UrpcClientFactory : AutoCloseable {
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

    /** The streaming connection's state. A factory without one reports [UrpcConnectionStatus.Connected]. */
    val connectionStatus: StateFlow<UrpcConnectionStatus>
        get() = AlwaysConnected

    /**
     * Checks the connection now instead of on schedule: retries a down or [UrpcConnectionStatus.Failed]
     * connection without waiting out its backoff, and pings a connected one.
     */
    fun reconnectNow() {}

    override fun close() {}
}

private val AlwaysConnected: StateFlow<UrpcConnectionStatus> = MutableStateFlow(UrpcConnectionStatus.Connected)
