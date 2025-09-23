package dev.isaacudy.udytils.error

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
class PresentableException private constructor(
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PresentableException

        if (errorMessage != other.errorMessage) return false
        if (cause != other.cause) return false

        return true
    }

    override fun hashCode(): Int {
        var result = errorMessage.hashCode()
        result = 31 * result + (cause?.hashCode() ?: 0)
        return result
    }
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

