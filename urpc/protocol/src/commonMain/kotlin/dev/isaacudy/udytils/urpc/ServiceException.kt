package dev.isaacudy.udytils.urpc

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.error.PresentableException

/**
 * Exception thrown by the client when the server returns an error response.
 *
 * Server-side handlers can also throw this to control the HTTP status code returned
 * to the caller, which the default [dev.isaacudy.udytils.urpc.server.ServiceErrorMapper]
 * will honour.
 */
class ServiceException(
    val statusCode: Int,
    val errorType: String?,
    errorMessage: ErrorMessage,
) : PresentableException(errorMessage, null)
