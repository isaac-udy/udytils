package dev.isaacudy.udytils.urpc.client.rest

import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.UrpcClientInterceptor
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Interceptors, 401 handling, custom routes, and what happens to calls the table doesn't cover. */
class RestUrpcCallLifecycleTest {

    private val getUser = unaryDescriptor<GetUserRequest, UserResponse>("userService.getUser")
    private val exportReport = unaryDescriptor<GetUserRequest, UserResponse>("userService.exportReport")
    private val userJson = """{"id":"1","name":"Ada"}"""

    @Test
    fun interceptorMetadataBecomesRequestHeaders() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            interceptors(
                UrpcClientInterceptor { context -> context.metadata["Authorization"] = "Bearer t" },
                UrpcClientInterceptor { context -> context.metadata["X-Wire-Name"] = context.wireName },
            )
            service("userService") { "getUser" via get("/users/{id}") }
        }

        urpc.callUnary(getUser, GetUserRequest(id = "1"))

        assertEquals("Bearer t", api.request.headers["Authorization"])
        assertEquals("userService.getUser", api.request.headers["X-Wire-Name"])
    }

    @Test
    fun interceptorMetadataOverridesAStaticHeaderOfTheSameName() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            interceptors(UrpcClientInterceptor { it.metadata["Authorization"] = "Bearer fresh" })
            service("userService") {
                headers("Authorization" to "Bearer static-default")
                "getUser" via get("/users/{id}")
            }
        }

        urpc.callUnary(getUser, GetUserRequest(id = "1"))

        assertEquals("Bearer fresh", api.request.headers["Authorization"])
    }

    @Test
    fun aFirstUnauthorizedRefreshesOnceAndRetriesWithTheChainReRun() = runTest {
        var token = "stale"
        var refreshes = 0
        val api = RecordingApi { _, attempt ->
            if (attempt == 1) respondJson("", HttpStatusCode.Unauthorized) else respondJson(userJson)
        }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            interceptors(UrpcClientInterceptor { it.metadata["Authorization"] = "Bearer $token" })
            tokenRefresher {
                refreshes++
                token = "fresh"
            }
            service("userService") { "getUser" via get("/users/{id}") }
        }

        assertEquals(UserResponse("1", "Ada"), urpc.callUnary(getUser, GetUserRequest(id = "1")))

        assertEquals(1, refreshes)
        assertEquals(2, api.requests.size)
        assertEquals("Bearer stale", api.requests[0].headers["Authorization"])
        assertEquals("Bearer fresh", api.requests[1].headers["Authorization"])
    }

    @Test
    fun aSecondUnauthorizedIsReportedRatherThanRetriedAgain() = runTest {
        var refreshes = 0
        val api = RecordingApi { _, _ -> respondJson("", HttpStatusCode.Unauthorized) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            tokenRefresher { refreshes++ }
            service("userService") { "getUser" via get("/users/{id}") }
        }

        val failure = assertFailsWith<ServiceException> {
            urpc.callUnary(getUser, GetUserRequest(id = "1"))
        }

        assertEquals(401, failure.statusCode)
        assertEquals(1, refreshes)
        assertEquals(2, api.requests.size)
    }

    @Test
    fun withoutARefresherA401IsJustAnError() = runTest {
        val api = RecordingApi { _, _ -> respondJson("", HttpStatusCode.Unauthorized) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "getUser" via get("/users/{id}") }
        }

        assertFailsWith<ServiceException> { urpc.callUnary(getUser, GetUserRequest(id = "1")) }

        assertEquals(1, api.requests.size)
    }

    @Test
    fun aCustomRouteOwnsTheExchangeAndSeesTheTypedRequest() = runTest {
        // Plain text, not JSON — the point of the escape hatch is that none of the marshalling
        // rules apply to it.
        val api = RecordingApi { _, _ -> respond("Ada from the escape hatch") }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            interceptors(UrpcClientInterceptor { it.metadata["Authorization"] = "Bearer t" })
            service("userService") {
                headers("X-Api-Key" to "abc123")
                "exportReport" via custom<GetUserRequest, UserResponse> { request ->
                    val response = httpClient.get("$baseUrl/reports/${request.id}") {
                        applyCallHeaders()
                    }
                    if (!response.status.isSuccess()) {
                        throw errorDecoder.decode(response.status.value, response.bodyAsText())
                    }
                    UserResponse(id = wireName, name = response.bodyAsText())
                }
            }
        }

        val result = urpc.callUnary(exportReport, GetUserRequest(id = "42"))

        assertEquals(UserResponse("userService.exportReport", "Ada from the escape hatch"), result)
        assertEquals("/reports/42", api.request.url.encodedPath)
        assertEquals("abc123", api.request.headers["X-Api-Key"])
        assertEquals("Bearer t", api.request.headers["Authorization"])
    }

    @Test
    fun aCustomRouteReusesTheConfiguredErrorDecoder() = runTest {
        val api = RecordingApi { _, _ -> respond("nope", HttpStatusCode.PaymentRequired) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") {
                "exportReport" via custom<GetUserRequest, UserResponse> { request ->
                    val response = httpClient.get("$baseUrl/reports/${request.id}")
                    if (!response.status.isSuccess()) {
                        throw errorDecoder.decode(response.status.value, response.bodyAsText())
                    }
                    UserResponse(id = wireName, name = response.bodyAsText())
                }
            }
        }

        val failure = assertFailsWith<ServiceException> {
            urpc.callUnary(exportReport, GetUserRequest(id = "42"))
        }

        assertEquals(402, failure.statusCode)
    }

    @Test
    fun anUnmappedUnaryCallGoesToTheFallback() = runTest {
        val fallback = RecordingFallback(unaryResult = UserResponse("1", "from urpc"))
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com", fallback = fallback) {
            service("userService") { "createUser" via post("/users") }
        }

        assertEquals(UserResponse("1", "from urpc"), urpc.callUnary(getUser, GetUserRequest(id = "1")))

        assertEquals(listOf("userService.getUser"), fallback.unaryCalls)
        assertTrue(api.requests.isEmpty(), "an unmapped call must not touch the REST API")
    }

    @Test
    fun anUnmappedStreamingCallGoesToTheFallback() = runTest {
        val fallback = RecordingFallback()
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com", fallback = fallback) {
            service("userService") { "getUser" via get("/users/{id}") }
        }

        urpc.callStreaming(
            streamingDescriptor<GetUserRequest, UserResponse>("userService.watch"),
            GetUserRequest("1"),
        )
        urpc.callBidirectional(
            bidirectionalDescriptor<GetUserRequest, UserResponse>("userService.session"),
            emptyFlow(),
        )

        assertEquals(listOf("userService.watch"), fallback.streamingCalls)
        assertEquals(listOf("userService.session"), fallback.bidirectionalCalls)
    }

    @Test
    fun aStreamingCallMappedToARestRouteFailsLoudlyAtTheCallSite() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com", fallback = RecordingFallback()) {
            service("userService") { "watch" via get("/users/{id}/watch") }
        }

        val failure = assertFailsWith<UnsupportedOperationException> {
            urpc.callStreaming(
                streamingDescriptor<GetUserRequest, UserResponse>("userService.watch"),
                GetUserRequest("1"),
            )
        }

        val message = failure.message.orEmpty()
        assertTrue("userService.watch" in message, message)
        assertTrue("GET /users/{id}/watch" in message, message)
        assertTrue("fallback" in message, message)
    }

    @Test
    fun anUnmappedCallWithNoFallbackNamesTheWireNameAndTheConfiguredServices() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "createUser" via post("/users") }
            service("orderService") { "listOrders" via get("/orders") }
        }

        val failure = assertFailsWith<RestUrpcException> {
            urpc.callUnary(getUser, GetUserRequest(id = "1"))
        }

        val message = failure.message.orEmpty()
        assertTrue("userService.getUser" in message, message)
        assertTrue("[orderService, userService]" in message, message)
        assertTrue("fallback" in message, message)
    }

    @Test
    fun mappedWireNamesReportsTheWholeTable() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") {
                "getUser" via get("/users/{id}")
                "createUser" via post("/users")
            }
            service("orderService") { "listOrders" via get("/orders") }
        }

        assertEquals(
            setOf("userService.getUser", "userService.createUser", "orderService.listOrders"),
            urpc.mappedWireNames,
        )
    }
}
