package dev.isaacudy.udytils.urpc

import kotlinx.serialization.json.Json

/**
 * Shared [Json] configuration used by both the urpc client and server when
 * serialising request and response payloads on the wire.
 */
val serviceFunctionJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    allowStructuredMapKeys = true
}
