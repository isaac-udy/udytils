package dev.isaacudy.udytils.urpc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Wire envelope for every server-to-client frame on a streaming or bidirectional
 * urpc call. Sits inside a single WebSocket text frame and discriminates between
 * a payload, an error, or — in the future — control signals like end-of-stream.
 *
 * Without this envelope the client would have to sniff each decoded frame for an
 * `"error"` field, which collides with any user response type that happens to use
 * `error` as a top-level property name. The discriminator (`"type"`) is reserved
 * by the framework — user payloads can use any field names without risk.
 */
@Serializable
@JsonClassDiscriminator("type")
sealed interface UrpcStreamingFrame<out T> {
    @Serializable
    @SerialName("data")
    data class Data<T>(val value: T) : UrpcStreamingFrame<T>

    @Serializable
    @SerialName("error")
    data class Error(
        val error: ServiceError,
        val statusCode: Int = 500,
    ) : UrpcStreamingFrame<Nothing>

    /**
     * Sent by the server when its response flow naturally completes, signalling
     * graceful end-of-stream. Lets the client end the call cleanly without
     * relying on the WebSocket close handshake (which races with in-flight
     * sends and confuses the auto-reconnect loop).
     */
    @Serializable
    @SerialName("complete")
    data object Complete : UrpcStreamingFrame<Nothing>
}
