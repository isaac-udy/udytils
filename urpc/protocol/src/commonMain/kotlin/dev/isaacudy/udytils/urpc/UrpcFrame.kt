@file:OptIn(ExperimentalSerializationApi::class)

package dev.isaacudy.udytils.urpc

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

/**
 * Wire envelope for the **multiplexed** urpc streaming transport, where many streaming
 * and bidirectional calls share a single WebSocket. Every frame carries a [callId] so the
 * receiver can route it to the right logical call. One frame == one WebSocket text frame
 * == one JSON object discriminated by `"type"`.
 *
 * This generalises [UrpcStreamingFrame] (which assumed one socket per call, and so needed
 * no id). Request/response payloads ride as raw [JsonElement] because a single socket
 * multiplexes calls with different Req/Res types — the transport defers deserialization
 * until it has routed the frame to a call and knows that call's descriptor/serializers.
 *
 * The `"type"` discriminator is reserved by the framework; user payloads (carried inside
 * [Open.payload] / [Data.payload] / [ClientData.payload]) may use any field names.
 */
@Serializable
@JsonClassDiscriminator("type")
sealed interface UrpcFrame {
    /** The logical call this frame belongs to. Connection-level frames use [CONNECTION]. */
    val callId: Long

    // ---- client -> server ----

    /**
     * Opens (or, on reconnect, re-opens) a logical call. [wireName] selects the service
     * function; [payload] is the serialised request (null for a Unit request); [metadata]
     * carries per-call "headers" populated by client interceptors and read on the server
     * (see [UrpcCallContext]).
     */
    @Serializable
    @SerialName("open")
    data class Open(
        override val callId: Long,
        val wireName: String,
        val payload: JsonElement? = null,
        val metadata: Map<String, String> = emptyMap(),
    ) : UrpcFrame

    /** A request item for a bidirectional call (an element of the client's request Flow). */
    @Serializable
    @SerialName("clientData")
    data class ClientData(
        override val callId: Long,
        val payload: JsonElement,
    ) : UrpcFrame

    /** The client's request Flow has completed — a bidirectional half-close. */
    @Serializable
    @SerialName("clientComplete")
    data class ClientComplete(override val callId: Long) : UrpcFrame

    /** Aborts a call (the consumer cancelled its Flow); the server cancels the handler. */
    @Serializable
    @SerialName("cancel")
    data class Cancel(override val callId: Long) : UrpcFrame

    // ---- server -> client ----

    /** A response value for [callId]. */
    @Serializable
    @SerialName("data")
    data class Data(
        override val callId: Long,
        val payload: JsonElement,
    ) : UrpcFrame

    /** A terminal error for [callId]. Ends only this call; the shared connection stays open. */
    @Serializable
    @SerialName("error")
    data class Error(
        override val callId: Long,
        val error: ServiceError,
        val statusCode: Int = 500,
    ) : UrpcFrame

    /** Graceful end-of-stream for [callId] (see [UrpcStreamingFrame.Complete] for the rationale). */
    @Serializable
    @SerialName("complete")
    data class Complete(override val callId: Long) : UrpcFrame

    // ---- connection-level ----

    /**
     * (Re)authenticates the whole connection. Sent at connect and whenever the client's
     * token is refreshed, so a long-lived socket can adopt a new token without dropping
     * and re-opening every in-flight call. Not tied to a logical call, so [callId] is
     * [CONNECTION].
     */
    @Serializable
    @SerialName("auth")
    data class Auth(
        val token: String,
        override val callId: Long = CONNECTION,
    ) : UrpcFrame

    companion object {
        /** The [callId] reserved for connection-level frames (e.g. [Auth]) that aren't tied to a call. */
        const val CONNECTION: Long = 0L
    }
}
