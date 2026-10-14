package dev.isaacudy.udytils.error

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThrowableSerializerTest {

    // Kotlin/JS has no qualified class names, so the serializer falls back to the simple name.
    private val expectedClassName = when (NamedException::class.qualifiedNameOrNull) {
        null -> "NamedException"
        else -> "dev.isaacudy.udytils.error.ThrowableSerializerTest.NamedException"
    }

    @Test
    fun className_isTheQualifiedName() {
        val encoded = Json.encodeToJsonElement(ThrowableSerializer, NamedException("boom"))

        assertEquals(
            expectedClassName,
            encoded.jsonObject.getValue("className").jsonPrimitive.content,
        )
    }

    @Test
    fun decoding_keepsTheClassNameAndMessage() {
        val json = Json.encodeToString(ThrowableSerializer, NamedException("boom"))
        val decoded = Json.decodeFromString(ThrowableSerializer, json)

        assertTrue(decoded.message.orEmpty().startsWith("$expectedClassName: boom"))
    }

    private class NamedException(message: String) : Exception(message)
}
