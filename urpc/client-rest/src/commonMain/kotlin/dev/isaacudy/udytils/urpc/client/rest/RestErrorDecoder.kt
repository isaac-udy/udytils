package dev.isaacudy.udytils.urpc.client.rest

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import kotlinx.serialization.json.Json

/**
 * Turns a non-2xx REST response into the exception a call site sees.
 *
 * Configured once for the whole route table via [RestUrpcConfig.errorDecoder]; the default is
 * [serviceErrorDecoder], which reproduces the native urpc client's mapping. Supply your own when
 * the API being wrapped reports errors in its own shape:
 *
 * ```
 * errorDecoder { statusCode, body ->
 *     val legacy = runCatching { json.decodeFromString<LegacyError>(body.orEmpty()) }.getOrNull()
 *     ServiceException(
 *         statusCode = statusCode,
 *         errorType = legacy?.code,
 *         errorMessage = ErrorMessage(title = "Request failed", message = legacy?.detail.orEmpty()),
 *     )
 * }
 * ```
 *
 * Throwing [ServiceException] keeps error handling identical on both sides of a migration, so
 * call sites don't have to change when an endpoint moves to native urpc.
 *
 * A 401 is decoded only after the [token refresher][RestUrpcConfig.tokenRefresher] has had its
 * one retry — the decoder never sees the first 401 of a refreshable call.
 */
fun interface RestErrorDecoder {
    /**
     * @param statusCode the HTTP status of the failed response.
     * @param body the response body as text, or `null` if the body could not be read at all.
     */
    fun decode(statusCode: Int, body: String?): Throwable
}

/**
 * The default [RestErrorDecoder]: decodes the body as a
 * [ServiceError][dev.isaacudy.udytils.urpc.ServiceError] and throws the resulting
 * [ServiceException], falling back to a generic `"HTTP <status>"` message when the body is
 * missing or is not a `ServiceError`.
 *
 * This is byte-for-byte the native urpc client's behaviour, so an API that already speaks
 * `ServiceError` — a urpc server fronted by a REST gateway, say — needs no decoder configuration
 * at all.
 *
 * @param json used to decode the error body — the route table passes its own [Json] here, so a
 *  custom [RestUrpcConfig.json] applies to error bodies as well as successful ones.
 */
fun serviceErrorDecoder(json: Json): RestErrorDecoder = RestErrorDecoder { statusCode, body ->
    val error = body?.let {
        runCatching { json.decodeFromString(ServiceError.serializer(), it) }.getOrNull()
    }
    ServiceException(
        statusCode = statusCode,
        errorType = error?.type,
        errorMessage = error?.message ?: ErrorMessage(
            title = "HTTP $statusCode",
            message = "An unknown error occurred",
        ),
    )
}
