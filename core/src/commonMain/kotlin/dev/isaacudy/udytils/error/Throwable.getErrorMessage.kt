package dev.isaacudy.udytils.error


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