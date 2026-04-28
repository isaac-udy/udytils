package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.ServiceException
import io.ktor.http.HttpStatusCode

/**
 * Marker for unauthenticated requests. Server handlers can throw this to map to a 401.
 */
class UnauthorizedException(message: String) : RuntimeException(message)

/**
 * Maps a [Throwable] thrown by a service handler to an HTTP status code returned to the
 * caller. Provide a custom implementation when you need to surface domain exceptions
 * with specific status codes.
 *
 * The default implementation honours [ServiceException.statusCode], maps
 * [UnauthorizedException] to 401, [IllegalArgumentException] / [IllegalStateException]
 * to 400, and falls back to 500 for everything else.
 */
fun interface ServiceErrorMapper {
    fun mapStatus(throwable: Throwable): HttpStatusCode

    companion object {
        // TODO(urpc): the original arcane-archivist mapper used `simpleName.contains(...)`
        // to recognise things like "InvalidCredentials" or "AccessDenied" without depending
        // on those exception types directly. That's brittle but does provide useful defaults
        // in a multi-module setup. Decide whether to (a) keep this minimal and let consumers
        // compose mappers, or (b) ship a richer default mapper that covers common cases.
        val Default: ServiceErrorMapper = ServiceErrorMapper { throwable ->
            when (throwable) {
                is ServiceException -> HttpStatusCode.fromValue(throwable.statusCode)
                is UnauthorizedException -> HttpStatusCode.Unauthorized
                is IllegalArgumentException -> HttpStatusCode.BadRequest
                is IllegalStateException -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.InternalServerError
            }
        }
    }
}
