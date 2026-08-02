package dev.isaacudy.udytils.urpc.client.rest

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/*
 * The contract these tests map onto REST. Hand-written stand-ins for what KSP generates: the
 * processor emits exactly this shape (ServiceDescriptor is public and documented for the
 * purpose), and the route table only ever sees `descriptor.name`, so nothing is lost by not
 * running the processor here.
 */

@Serializable
internal data class GetUserRequest(
    val id: String,
    val expand: List<String> = emptyList(),
    val includeArchived: Boolean = false,
    val search: String? = null,
)

@Serializable
internal data class UserResponse(val id: String, val name: String)

@Serializable
internal data class CreateUserRequest(val name: String, val email: String)

@Serializable
internal data class UpdateUserRequest(val id: String, val name: String)

@Serializable
internal data class FilteredRequest(val id: String, val filter: Filter)

@Serializable
internal data class Filter(val min: Int, val max: Int)

internal inline fun <reified Req, reified Res> unaryDescriptor(
    name: String,
    isUnitRequest: Boolean = false,
    isUnitResponse: Boolean = false,
) = ServiceDescriptor(
    name = name,
    requestSerializer = serializer<Req>(),
    responseSerializer = serializer<Res>(),
    isUnitRequest = isUnitRequest,
    isUnitResponse = isUnitResponse,
)

internal inline fun <reified Req, reified Res> streamingDescriptor(name: String) =
    StreamingServiceDescriptor(
        name = name,
        requestSerializer = serializer<Req>(),
        responseSerializer = serializer<Res>(),
        isUnitRequest = false,
    )

internal inline fun <reified Req, reified Res> bidirectionalDescriptor(name: String) =
    BidirectionalServiceDescriptor(
        name = name,
        requestSerializer = serializer<Req>(),
        responseSerializer = serializer<Res>(),
    )

/**
 * A [MockEngine]-backed API that records every request it serves.
 *
 * The handler receives the 1-based attempt number so a test can answer differently on a retry
 * without keeping its own counter.
 */
internal class RecordingApi(
    private val handle: MockRequestHandleScope.(HttpRequestData, Int) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()

    val client = HttpClient(
        MockEngine { request ->
            requests += request
            handle(request, requests.size)
        },
    )

    val request: HttpRequestData get() = requests.single()
}

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

internal val HttpRequestData.bodyText: String? get() = (body as? TextContent)?.text

/** A [UrpcClientFactory] that records what it was asked for; stands in for the native client. */
internal class RecordingFallback(private val unaryResult: Any? = null) : UrpcClientFactory {

    val unaryCalls = mutableListOf<String>()
    val streamingCalls = mutableListOf<String>()
    val bidirectionalCalls = mutableListOf<String>()

    override suspend fun <Req, Res> callUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        request: Req,
    ): Res {
        unaryCalls += descriptor.name
        @Suppress("UNCHECKED_CAST")
        return unaryResult as Res
    }

    override fun <Req, Res> callStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        request: Req,
    ): Flow<Res> {
        streamingCalls += descriptor.name
        return emptyFlow()
    }

    override fun <Req, Res> callBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        requests: Flow<Req>,
    ): Flow<Res> {
        bidirectionalCalls += descriptor.name
        return emptyFlow()
    }
}
