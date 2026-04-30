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
        /**
         * Recognises the types shipped with urpc itself ([ServiceException],
         * [UnauthorizedException]) plus a couple of standard-library exception
         * shapes. Deliberately stays minimal — consumers who need richer mapping
         * (domain exceptions like `AccessDeniedException`, `EntityNotFoundException`,
         * etc.) should compose their own [ServiceErrorMapper] instead of relying on
         * fragile name-based dispatch baked into the framework default.
         */
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
