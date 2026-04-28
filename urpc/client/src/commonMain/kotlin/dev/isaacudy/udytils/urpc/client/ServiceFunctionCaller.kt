package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.KSerializer

@PublishedApi
internal class ServiceFunctionCaller<Request : Any, Response : Any>(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val path: String,
    private val requestSerializer: KSerializer<Request>,
    private val responseSerializer: KSerializer<Response>,
    private val isUnitRequest: Boolean,
    private val isUnitResponse: Boolean,
    private val authTokenProvider: () -> String?,
    private val tokenRefresher: suspend () -> Unit,
) {
    suspend fun call(request: Request): Response {
        val result = execute(request)
        if (result.status == HttpStatusCode.Unauthorized) {
            runCatching { tokenRefresher() }
            val retryResult = execute(request)
            return parseServiceResponse(retryResult, isUnitResponse, responseSerializer)
        }
        return parseServiceResponse(result, isUnitResponse, responseSerializer)
    }

    private suspend fun execute(request: Request): HttpResponse {
        return httpClient.post("$baseUrl/services/$path") {
            authTokenProvider()?.let { header("Authorization", "Bearer $it") }
            if (!isUnitRequest) {
                contentType(ContentType.Application.Json)
                setBody(serviceFunctionJson.encodeToString(requestSerializer, request))
            }
        }
    }
}
