package dev.isaacudy.udytils.error

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class ThrowableSurrogate(
    val message: String?,
    val className: String?,
    val stackTrace: String,
    val cause: ThrowableSurrogate?
)

// 2. Custom Serializer
object ThrowableSerializer : KSerializer<Throwable> {
    override val descriptor: SerialDescriptor = ThrowableSurrogate.serializer().descriptor
    private val surrogateSerializer = ThrowableSurrogate.serializer()

    override fun serialize(encoder: Encoder, value: Throwable) {
        val surrogate = ThrowableSurrogate(
            message = value.message,
            className = value::class.qualifiedName,
            stackTrace = value.stackTraceToString(),
            cause = value.cause?.let {
                // Note: Potential recursion depth issues here
                // Consider limiting cause depth
                null // simplified for example
            }
        )
        encoder.encodeSerializableValue(surrogateSerializer, surrogate)
    }

    override fun deserialize(decoder: Decoder): Throwable {
        val surrogate = decoder.decodeSerializableValue(surrogateSerializer)
        return Exception("${surrogate.className}: ${surrogate.message}\n${surrogate.stackTrace}")
    }
}