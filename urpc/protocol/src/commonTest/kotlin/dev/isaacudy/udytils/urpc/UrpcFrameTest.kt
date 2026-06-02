package dev.isaacudy.udytils.urpc

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrpcFrameTest {

    private fun encode(frame: UrpcFrame): String =
        serviceFunctionJson.encodeToString(UrpcFrame.serializer(), frame)

    private fun decode(json: String): UrpcFrame =
        serviceFunctionJson.decodeFromString(UrpcFrame.serializer(), json)

    private fun roundTrip(frame: UrpcFrame): UrpcFrame = decode(encode(frame))

    /** The on-wire `"type"` discriminator value for a frame. */
    private fun typeOf(frame: UrpcFrame): String =
        serviceFunctionJson.parseToJsonElement(encode(frame)).jsonObject["type"]!!.jsonPrimitive.content

    @Test
    fun open_roundTrips_with_payload_and_metadata() {
        val frame = UrpcFrame.Open(
            callId = 7,
            wireName = "campaign.getCampaigns",
            payload = buildJsonObject { put("limit", 10) },
            metadata = mapOf("authorization" to "Bearer abc"),
        )
        assertEquals(frame, roundTrip(frame))
        assertEquals("open", typeOf(frame))
    }

    @Test
    fun open_defaults_to_null_payload_and_empty_metadata() {
        val frame = UrpcFrame.Open(callId = 1, wireName = "x.y")
        val decoded = roundTrip(frame) as UrpcFrame.Open
        assertNull(decoded.payload)
        assertTrue(decoded.metadata.isEmpty())
    }

    @Test
    fun data_frame_carries_callId_and_type_on_wire() {
        val frame = UrpcFrame.Data(callId = 42, payload = JsonPrimitive("hello"))
        assertEquals(frame, roundTrip(frame))
        val obj = serviceFunctionJson.parseToJsonElement(encode(frame)).jsonObject
        assertEquals(42L, obj["callId"]!!.jsonPrimitive.content.toLong())
        assertEquals("data", obj["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun error_frame_carries_serviceError_and_status() {
        val frame = UrpcFrame.Error(
            callId = 3,
            error = ServiceError(type = "Boom", message = null),
            statusCode = 418,
        )
        val decoded = roundTrip(frame) as UrpcFrame.Error
        assertEquals(3L, decoded.callId)
        assertEquals("Boom", decoded.error.type)
        assertEquals(418, decoded.statusCode)
        assertEquals("error", typeOf(frame))
    }

    @Test
    fun control_frames_roundTrip() {
        assertEquals(UrpcFrame.Complete(5), roundTrip(UrpcFrame.Complete(5)))
        assertEquals(UrpcFrame.Cancel(5), roundTrip(UrpcFrame.Cancel(5)))
        assertEquals(UrpcFrame.ClientComplete(5), roundTrip(UrpcFrame.ClientComplete(5)))
        assertEquals(
            UrpcFrame.ClientData(5, JsonPrimitive(1)),
            roundTrip(UrpcFrame.ClientData(5, JsonPrimitive(1))),
        )
        assertEquals("complete", typeOf(UrpcFrame.Complete(5)))
        assertEquals("cancel", typeOf(UrpcFrame.Cancel(5)))
    }

    @Test
    fun auth_is_connection_level() {
        val frame = UrpcFrame.Auth(token = "jwt")
        assertEquals(UrpcFrame.CONNECTION, frame.callId)
        assertEquals(frame, roundTrip(frame))
        assertEquals("auth", typeOf(frame))
    }
}
