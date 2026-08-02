package dev.isaacudy.udytils.urpc.client.rest

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** How a REST response becomes the contract's return value — or the contract's exception. */
class RestResponseMappingTest {

    private val getUser = unaryDescriptor<GetUserRequest, UserResponse>("userService.getUser")
    private val search = unaryDescriptor<CreateUserRequest, UserResponse>("userService.search")
    private val deleteUser = unaryDescriptor<GetUserRequest, Unit>("userService.deleteUser", isUnitResponse = true)

    @Test
    fun aSuccessfulBodyIsDecodedWithTheContractsSerializer() = runTest {
        val api = RecordingApi { _, _ -> respondJson("""{"id":"1","name":"Ada"}""") }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "getUser" via get("/users/{id}") }
        }

        assertEquals(UserResponse(id = "1", name = "Ada"), urpc.callUnary(getUser, GetUserRequest(id = "1")))
    }

    @Test
    fun aUnitReturnIgnoresWhateverTheApiSendsBack() = runTest {
        val api = RecordingApi { _, _ -> respondJson("<html>deleted</html>") }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "deleteUser" via delete("/users/{id}") }
        }

        assertEquals(Unit, urpc.callUnary(deleteUser, GetUserRequest(id = "1")))
    }

    @Test
    fun aResponseTransformUnwrapsAnEnvelopeTheContractDoesNotModel() = runTest {
        val api = RecordingApi { _, _ ->
            respondJson("""{"meta":{"took":3},"data":{"id":"1","name":"Ada"}}""")
        }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") {
                "search" via post("/search") { response { it.jsonObject.getValue("data") } }
            }
        }

        assertEquals(
            UserResponse(id = "1", name = "Ada"),
            urpc.callUnary(search, CreateUserRequest(name = "Ada", email = "ada@example.com")),
        )
    }

    @Test
    fun aBodyTheContractCannotReadNamesTheCallAndTheRoute() = runTest {
        val api = RecordingApi { _, _ -> respondJson("""{"identifier":"1"}""") }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "getUser" via get("/users/{id}") }
        }

        val failure = assertFailsWith<RestUrpcException> {
            urpc.callUnary(getUser, GetUserRequest(id = "1"))
        }

        val message = failure.message.orEmpty()
        assertTrue("userService.getUser" in message, message)
        assertTrue("GET /users/{id}" in message, message)
    }

    @Test
    fun theDefaultDecoderReproducesTheNativeServiceErrorMapping() = runTest {
        // Exactly what a urpc server puts on the wire, so this asserts real parity rather than
        // agreement with a hand-written approximation of ServiceError's JSON shape.
        val serviceError = serviceFunctionJson.encodeToString(
            ServiceError.serializer(),
            ServiceError(
                type = "UnauthorizedException",
                message = ErrorMessage(title = "Nope", message = "You may not do that"),
            ),
        )
        val api = RecordingApi { _, _ -> respondJson(serviceError, HttpStatusCode.Forbidden) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "getUser" via get("/users/{id}") }
        }

        val failure = assertFailsWith<ServiceException> {
            urpc.callUnary(getUser, GetUserRequest(id = "1"))
        }

        assertEquals(403, failure.statusCode)
        assertEquals("UnauthorizedException", failure.errorType)
        assertEquals("Nope", failure.errorMessage.title.string)
        assertEquals("You may not do that", failure.errorMessage.message.string)
    }

    @Test
    fun theDefaultDecoderFallsBackWhenTheBodyIsNotAServiceError() = runTest {
        val api = RecordingApi { _, _ -> respondJson("<html>502 Bad Gateway</html>", HttpStatusCode.BadGateway) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            service("userService") { "getUser" via get("/users/{id}") }
        }

        val failure = assertFailsWith<ServiceException> {
            urpc.callUnary(getUser, GetUserRequest(id = "1"))
        }

        assertEquals(502, failure.statusCode)
        assertEquals(null, failure.errorType)
        assertEquals("HTTP 502", failure.errorMessage.title.string)
        assertEquals("An unknown error occurred", failure.errorMessage.message.string)
    }

    @Test
    fun aCustomDecoderSeesTheStatusAndTheRawBody() = runTest {
        val api = RecordingApi { _, _ -> respondJson("""{"code":"OVER_QUOTA"}""", HttpStatusCode.TooManyRequests) }
        val urpc = api.client.restUrpcClient("https://api.example.com") {
            errorDecoder { statusCode, body -> IllegalStateException("$statusCode/$body") }
            service("userService") { "getUser" via get("/users/{id}") }
        }

        val failure = assertFailsWith<IllegalStateException> {
            urpc.callUnary(getUser, GetUserRequest(id = "1"))
        }

        assertEquals("""429/{"code":"OVER_QUOTA"}""", failure.message)
    }
}
