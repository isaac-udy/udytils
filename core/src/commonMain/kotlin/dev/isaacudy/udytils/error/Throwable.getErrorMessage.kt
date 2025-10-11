package dev.isaacudy.udytils.error

import dev.isaacudy.udytils.UdytilsConfig

fun Throwable.getErrorMessage(): ErrorMessage {
    return when(this) {
        is PresentableException -> this.errorMessage
        else -> when {
            UdytilsConfig.showExceptionMessagesDirectly -> {
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

fun Throwable.getErrorMessageOrNull(): ErrorMessage? {
    return when(this) {
        is PresentableException -> this.errorMessage
        else -> null
    }
}