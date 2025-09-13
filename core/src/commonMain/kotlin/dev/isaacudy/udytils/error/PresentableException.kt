package dev.isaacudy.udytils.error

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

