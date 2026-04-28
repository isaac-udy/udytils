package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer

internal suspend fun <Res : Any> parseServiceResponse(
    response: HttpResponse,
    isUnitResponse: Boolean,
    serializer: KSerializer<Res>,
): Res {
    if (!response.status.isSuccess()) throwServiceError(response)
    if (isUnitResponse) {
        @Suppress("UNCHECKED_CAST")
        return Unit as Res
    }
    return serviceFunctionJson.decodeFromString(serializer, response.bodyAsText())
}

private suspend fun throwServiceError(response: HttpResponse): Nothing {
    val body = runCatching { response.bodyAsText() }.getOrNull()
    val error = body?.let {
        runCatching {
            serviceFunctionJson.decodeFromString(ServiceError.serializer(), it)
        }.getOrNull()
    }
    throw ServiceException(
        statusCode = response.status.value,
        errorType = error?.type,
        errorMessage = error?.message ?: ErrorMessage(
            title = "HTTP ${response.status.value}",
            message = "An unknown error occurred",
        ),
    )
}
