package dev.isaacudy.udytils.error

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThrowableSerializerTest {

    @Test
    fun className_isTheQualifiedName() {
        val encoded = Json.encodeToJsonElement(ThrowableSerializer, NamedException("boom"))

        assertEquals(
            "dev.isaacudy.udytils.error.ThrowableSerializerTest.NamedException",
            encoded.jsonObject.getValue("className").jsonPrimitive.content,
        )
    }

    @Test
    fun decoding_keepsTheClassNameAndMessage() {
        val json = Json.encodeToString(ThrowableSerializer, NamedException("boom"))
        val decoded = Json.decodeFromString(ThrowableSerializer, json)

        assertTrue(decoded.message.orEmpty().startsWith("dev.isaacudy.udytils.error.ThrowableSerializerTest.NamedException: boom"))
    }

    private class NamedException(message: String) : Exception(message)
}
