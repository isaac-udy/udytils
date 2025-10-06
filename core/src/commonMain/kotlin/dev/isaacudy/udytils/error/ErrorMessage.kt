package dev.isaacudy.udytils.error

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Serializable
class ErrorMessage private constructor(
    var id: String,
    val title: String,
    val message: String,
    val retryable: Boolean,
    val isUnknown: Boolean,
) {
    constructor(
        title: String,
        message: String,
        retryable: Boolean,
        from: Any = title,
    ) : this(
        id = getErrorIdFrom(from),
        title = title,
        message = message,
        retryable = retryable,
        isUnknown = from is Throwable,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ErrorMessage

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

private fun getErrorIdFrom(from: Any): String {
    val from = from
    val className = from::class.simpleName ?: "Unknown"
    val typeName = when(from) {
        is String -> from.take(24)
        else -> className
    }.removeSuffix("Exception")
        .removeSuffix("Error")

    return Base64.Default.encode(typeName.encodeToByteArray())
}