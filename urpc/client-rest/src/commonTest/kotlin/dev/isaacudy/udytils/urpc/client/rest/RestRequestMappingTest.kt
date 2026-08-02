package dev.isaacudy.udytils.urpc.client.rest

import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How a typed urpc request becomes a REST path, query string and body. */
class RestRequestMappingTest {

    private val getUser = unaryDescriptor<GetUserRequest, UserResponse>("userService.getUser")
    private val createUser = unaryDescriptor<CreateUserRequest, UserResponse>("userService.createUser")
    private val updateUser = unaryDescriptor<UpdateUserRequest, UserResponse>("userService.updateUser")
    private val deleteUser = unaryDescriptor<GetUserRequest, Unit>("userService.deleteUser", isUnitResponse = true)
    private val whoAmI = unaryDescriptor<Unit, UserResponse>("userService.whoAmI", isUnitRequest = true)
    private val filtered = unaryDescriptor<FilteredRequest, UserResponse>("userService.filtered")

    private val userJson = """{"id":"1","name":"Ada"}"""

    @Test
    fun pathPlaceholdersAreFilledFromRequestFieldsAndUrlEncoded() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "getUser" via get("/users/{id}") }
        }

        urpc.callUnary(getUser, GetUserRequest(id = "ada lovelace/1"))

        assertEquals(HttpMethod.Get, api.request.method)
        assertEquals("/users/ada%20lovelace%2F1", api.request.url.encodedPath)
    }

    @Test
    fun fieldsLeftOverFromThePathBecomeQueryParameters() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "getUser" via get("/users/{id}") }
        }

        urpc.callUnary(
            getUser,
            GetUserRequest(id = "1", expand = listOf("orders", "roles"), includeArchived = true, search = null),
        )

        val parameters = api.request.url.parameters
        assertEquals("/users/1", api.request.url.encodedPath)
        assertEquals(listOf("orders", "roles"), parameters.getAll("expand"))
        assertEquals("true", parameters["includeArchived"])
        // A JSON null is spelled in REST as an absent parameter, not the literal "null".
        assertNull(parameters["search"])
        // The path consumed it, so it must not also appear in the query string.
        assertNull(parameters["id"])
    }

    @Test
    fun aNestedObjectInAQueryStringIsACallTimeErrorPointingAtCustom() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "filtered" via get("/users/{id}") }
        }

        val failure = assertFailsWith<RestUrpcException> {
            urpc.callUnary(filtered, FilteredRequest(id = "1", filter = Filter(min = 1, max = 9)))
        }

        val message = failure.message.orEmpty()
        assertTrue("userService.filtered" in message, message)
        assertTrue("filter" in message, message)
        assertTrue("custom { }" in message, message)
        assertTrue(api.requests.isEmpty(), "the request should never have been sent")
    }

    @Test
    fun bodyRoutesSendEverythingThePathDidNotConsume() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "updateUser" via put("/users/{id}") }
        }

        urpc.callUnary(updateUser, UpdateUserRequest(id = "1", name = "Ada"))

        assertEquals(HttpMethod.Put, api.request.method)
        assertEquals("/users/1", api.request.url.encodedPath)
        assertEquals("""{"name":"Ada"}""", api.request.bodyText)
    }

    @Test
    fun aBodyRouteWithNoPlaceholdersSendsTheWholeRequest() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "createUser" via post("/users") }
        }

        urpc.callUnary(createUser, CreateUserRequest(name = "Ada", email = "ada@example.com"))

        assertEquals(HttpMethod.Post, api.request.method)
        assertEquals("/users", api.request.url.encodedPath)
        assertEquals(
            Json.parseToJsonElement("""{"name":"Ada","email":"ada@example.com"}"""),
            Json.parseToJsonElement(api.request.bodyText.orEmpty()),
        )
    }

    @Test
    fun deleteSpillsToTheQueryStringRatherThanABody() = runTest {
        val api = RecordingApi { _, _ -> respondJson("") }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "deleteUser" via delete("/users/{id}") }
        }

        urpc.callUnary(deleteUser, GetUserRequest(id = "1", includeArchived = true))

        assertEquals(HttpMethod.Delete, api.request.method)
        assertNull(api.request.bodyText)
        assertEquals("true", api.request.url.parameters["includeArchived"])
    }

    @Test
    fun aUnitRequestSendsNoBodyAndNoQueryString() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "whoAmI" via get("/users/me") }
        }

        urpc.callUnary(whoAmI, Unit)

        assertEquals("/users/me", api.request.url.encodedPath)
        assertNull(api.request.bodyText)
        assertTrue(api.request.url.parameters.isEmpty(), "a Unit request has nothing to send")
    }

    @Test
    fun aUnitRequestCannotFillAPathPlaceholder() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "whoAmI" via get("/users/{id}") }
        }

        val failure = assertFailsWith<RestUrpcException> { urpc.callUnary(whoAmI, Unit) }

        val message = failure.message.orEmpty()
        assertTrue("userService.whoAmI" in message, message)
        assertTrue("[id]" in message, message)
    }

    @Test
    fun aPlaceholderNamingAFieldTheRequestDoesNotHaveIsAnActionableError() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "createUser" via post("/users/{id}") }
        }

        val failure = assertFailsWith<RestUrpcException> {
            urpc.callUnary(createUser, CreateUserRequest(name = "Ada", email = "ada@example.com"))
        }

        val message = failure.message.orEmpty()
        assertTrue("{id}" in message, message)
        assertTrue("userService.createUser" in message, message)
        // The available fields are listed, which is what makes the message actionable.
        assertTrue("[email, name]" in message, message)
    }

    @Test
    fun aNonPrimitivePathFieldIsACallTimeError() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "filtered" via post("/users/{filter}") }
        }

        val failure = assertFailsWith<RestUrpcException> {
            urpc.callUnary(filtered, FilteredRequest(id = "1", filter = Filter(min = 1, max = 9)))
        }

        val message = failure.message.orEmpty()
        assertTrue("{filter}" in message, message)
        assertTrue("object" in message, message)
    }

    @Test
    fun serviceHeadersAreSentOnEveryRouteOfThatService() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") {
                headers("X-Api-Key" to "abc123", "X-Api-Version" to "2")
                "getUser" via get("/users/{id}")
            }
        }

        urpc.callUnary(getUser, GetUserRequest(id = "1"))

        assertEquals("abc123", api.request.headers["X-Api-Key"])
        assertEquals("2", api.request.headers["X-Api-Version"])
    }

    @Test
    fun theConfiguredJsonIsUsedToEncodeTheRequest() = runTest {
        val api = RecordingApi { _, _ -> respondJson(userJson) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            // The REST API treats an absent field as "leave it alone", which urpc's shared Json
            // (encodeDefaults = true) would defeat by sending every default on every call.
            json = Json { encodeDefaults = false }
            service("userService") { "getUser" via post("/users/search") }
        }

        urpc.callUnary(getUser, GetUserRequest(id = "1"))

        val body = Json.parseToJsonElement(api.request.bodyText.orEmpty()) as JsonObject
        assertEquals(setOf("id"), body.keys)
    }
}
