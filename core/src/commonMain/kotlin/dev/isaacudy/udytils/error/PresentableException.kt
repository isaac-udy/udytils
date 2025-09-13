package dev.isaacudy.udytils.error

import kotlin.io.encoding.Base64

private val showExceptionMessagesDirectly = true

open class PresentableException private constructor(
    val errorMessage: ErrorMessage,
) : RuntimeException(errorMessage.title) {
    constructor(
        title: String,
        message: String = "",
        retryable: Boolean = true,
    ) : this(
        ErrorMessage(
            title = title,
            message = message,
            retryable = retryable,
        )
    )
}

fun presentableException(
    title: String,
    message: String,
    retryable: Boolean = true,
) : PresentableException {
    return PresentableException(
        title = title,
        message = message,
        retryable = retryable,
    ).apply {
        errorMessage.from = title
    }
}

@ConsistentCopyVisibility
data class ErrorMessage internal constructor(
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

        return Base64.encode(typeName.encodeToByteArray())
    }
}

fun Throwable.getErrorMessage(): ErrorMessage {
    val message = when(this) {
        is PresentableException -> this.errorMessage
        else -> when {
            showExceptionMessagesDirectly -> {
                ErrorMessage(
                    title = this::class.simpleName ?: "Unexpected error",
                    message = this.message ?: "Unexpected error",
                    retryable = true,
                )
            }
            else -> {
                ErrorMessage(
                    title = "Unexpected error",
                    message = "An unexpected error occurred. Please try again later.",
                    retryable = true,
                )
            }
        }
    }
    if (message.from == null) {
        message.from = this
    }
    return message
}
