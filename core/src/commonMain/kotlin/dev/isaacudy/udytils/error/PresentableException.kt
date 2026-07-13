package dev.isaacudy.udytils.error

import dev.isaacudy.udytils.string.StringOrResource
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * An exception carrying a user-facing [ErrorMessage], so the UI can show a meaningful
 * title/message instead of a generic failure.
 *
 * Throw (or wrap causes in) a `PresentableException` anywhere below the UI layer;
 * [Throwable.getErrorMessage] recovers the message at the presentation layer, and
 * [Throwable.isRetryable] reads [ErrorMessage.retryable] to decide whether retry UI or automatic
 * retries apply. Serializable, so it can cross urpc boundaries. Equality is by [errorMessage]
 * and [cause].
 */
@Serializable
open class PresentableException(
    val errorMessage: ErrorMessage,
    @Serializable(with = ThrowableSerializer::class)
    override val cause: Throwable?,
) : RuntimeException(errorMessage.title.string ?: errorMessage.title.resourceKey) {
    constructor(
        title: String,
        message: String = "",
        retryable: Boolean = true,
        cause: Throwable? = null,
    ) : this(
        ErrorMessage(
            title = title,
            message = message,
            retryable = retryable,
        ),
        cause = cause,
    )

    constructor(
        title: StringResource,
        message: StringResource,
        retryable: Boolean = true,
        cause: Throwable? = null,
    ) : this(
        ErrorMessage(
            title = title,
            message = message,
            retryable = retryable,
        ),
        cause = cause,
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

/**
 * Creates a [PresentableException] with a plain-string [title] and [message]. Equivalent to the
 * constructor; reads more naturally at call sites.
 */
fun presentableException(
    title: String,
    message: String,
    retryable: Boolean = true,
    cause: Throwable? = null,
) : PresentableException {
    return PresentableException(
        title = title,
        message = message,
        retryable = retryable,
        cause = cause,
    )
}

/**
 * Creates a [PresentableException] whose [title] and [message] are localisable compose-resources
 * [StringResource]s, resolved to display text at presentation time.
 */
fun presentableException(
    title: StringResource,
    message: StringResource,
    retryable: Boolean = true,
    cause: Throwable? = null,
) : PresentableException {
    return PresentableException(
        title = title,
        message = message,
        retryable = retryable,
        cause = cause,
    )
}

