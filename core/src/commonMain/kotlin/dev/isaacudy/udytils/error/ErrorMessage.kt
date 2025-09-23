package dev.isaacudy.udytils.error

import kotlin.io.encoding.Base64

class ErrorMessage internal constructor(
    val title: String,
    val message: String,
    val retryable: Boolean,
) {
    internal var from: Any? = null

    val errorId: String get() {
        val from = from ?: "Unknown"
        val className = from::class.simpleName ?: "Unknown"
        val typeName = when(from) {
            is String -> from.take(24)
            else -> className
        }.removeSuffix("Exception")
            .removeSuffix("Error")

        return Base64.Default.encode(typeName.encodeToByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ErrorMessage

        return errorId == other.errorId
    }

    override fun hashCode(): Int {
        return errorId.hashCode()
    }
}