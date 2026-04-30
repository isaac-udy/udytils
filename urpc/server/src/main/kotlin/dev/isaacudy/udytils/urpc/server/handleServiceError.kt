package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException

/**
 * Maps a service-handler exception to an HTTP error response using [errorMapper] and
 * sends a JSON [ServiceError] body.
 */
suspend fun handleServiceError(
    call: ApplicationCall,
    throwable: Throwable,
    errorMapper: ServiceErrorMapper = ServiceErrorMapper.Default,
    logger: UrpcLogger = UrpcLogger.NoOp,
) {
    if (throwable is CancellationException) throw throwable

    val status = errorMapper.mapStatus(throwable)
    val error = ServiceError.from(throwable)

    if (status == HttpStatusCode.InternalServerError) {
        logger.error("Service function error", throwable)
    } else {
        logger.debug("Service function error: ${status.value} ${error.type}: ${error.message}")
    }

    call.respond(status, serviceFunctionJson.encodeToString(ServiceError.serializer(), error))
}
