package dev.isaacudy.udytils.error

import dev.isaacudy.udytils.string.StringOrResource
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import kotlin.io.encoding.Base64

@Serializable
class ErrorMessage private constructor(
    var id: String,
    val title: StringOrResource,
    val message: StringOrResource,
    val retryable: Boolean,
    val isUnknown: Boolean,
) {
    constructor(
        title: String,
        message: String,
        retryable: Boolean = false,
        from: Any = title,
    ) : this(
        id = getErrorIdFrom(from),
        title = StringOrResource(title),
        message = StringOrResource(message),
        retryable = retryable,
        isUnknown = from is Throwable,
    )

    constructor(
        title: StringResource,
        message: StringResource,
        retryable: Boolean = false,
        from: Any = title,
    ) : this(
        id = getErrorIdFrom(from),
        title = StringOrResource(title),
        message = StringOrResource(message),
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

    companion object {
        var showExceptionMessagesDirectly = false
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

    val throwableMessage = when (from) {
        is Throwable -> from.message
        else -> null
    }
    val errorId = when(typeName) {
        "IllegalState" -> "ISE/$throwableMessage"
        "IllegalArgument" -> "IAE/$throwableMessage"
        "Runtime" -> "RE/$throwableMessage"
        else -> typeName
    }

    return Base64.Default.encode(errorId.encodeToByteArray())
}
