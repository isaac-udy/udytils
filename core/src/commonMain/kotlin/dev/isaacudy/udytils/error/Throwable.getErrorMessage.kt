package dev.isaacudy.udytils.error

/**
 * The user-presentable [ErrorMessage] for this throwable.
 *
 * A [PresentableException] returns its own [ErrorMessage]; any other throwable maps to a
 * generic, retryable "Unexpected error" — or to the exception's own class and message when
 * [ErrorMessage.showExceptionMessagesDirectly] is enabled (e.g. in debug builds).
 */
fun Throwable.getErrorMessage(): ErrorMessage {
    return when(this) {
        is PresentableException -> this.errorMessage
        else -> when {
            ErrorMessage.showExceptionMessagesDirectly -> {
                ErrorMessage(
                    title = this::class.simpleName ?: "Unexpected error",
                    message = this.message ?: "Unexpected error",
                    retryable = true,
                    from = this,
                )
            }
            else -> {
                ErrorMessage(
                    title = "Unexpected error",
                    message = "An unexpected error occurred. Please try again later.",
                    retryable = true,
                    from = this,
                )
            }
        }
    }
}

/**
 * The [ErrorMessage] of a [PresentableException], or null for any other throwable — use this to
 * distinguish deliberately presented errors from unexpected ones.
 */
fun Throwable.getErrorMessageOrNull(): ErrorMessage? {
    return when(this) {
        is PresentableException -> this.errorMessage
        else -> null
    }
}