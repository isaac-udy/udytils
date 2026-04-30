package dev.isaacudy.udytils.urpc

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.error.getErrorMessage
import kotlinx.serialization.Serializable

/**
 * JSON error response body for service function errors. Shared between client and server.
 */
@Serializable
data class ServiceError(
    val type: String,
    val message: ErrorMessage?,
) {
    companion object {
        fun from(throwable: Throwable) = ServiceError(
            type = throwable::class.simpleName ?: "Unknown",
            message = throwable.getErrorMessage(),
        )
    }
}
