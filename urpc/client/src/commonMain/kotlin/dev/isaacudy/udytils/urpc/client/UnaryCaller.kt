package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

internal class UnaryCaller<Req, Res>(
    private val factory: UrpcClientFactory,
    private val descriptor: ServiceDescriptor<Req, Res>,
) {
    suspend fun call(request: Req): Res {
        val result = execute(request)
        if (result.status == HttpStatusCode.Unauthorized) {
            runCatching { factory.tokenRefresher() }
            return parseResponse(execute(request))
        }
        return parseResponse(result)
    }

    private suspend fun execute(request: Req): HttpResponse {
        return factory.httpClient.post("${factory.baseUrl}/services/${descriptor.name}") {
            factory.authTokenProvider()?.let { header("Authorization", "Bearer $it") }
            if (!descriptor.isUnitRequest) {
                contentType(ContentType.Application.Json)
                setBody(serviceFunctionJson.encodeToString(descriptor.requestSerializer, request))
            }
        }
    }

    private suspend fun parseResponse(response: HttpResponse): Res {
        if (!response.status.isSuccess()) throwServiceError(response)
        if (descriptor.isUnitResponse) {
            @Suppress("UNCHECKED_CAST")
            return Unit as Res
        }
        return serviceFunctionJson.decodeFromString(descriptor.responseSerializer, response.bodyAsText())
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
}
