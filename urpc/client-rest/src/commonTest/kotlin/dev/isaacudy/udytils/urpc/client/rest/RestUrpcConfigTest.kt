package dev.isaacudy.udytils.urpc.client.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A route table is built once at startup, so a mistake in it should surface there rather than on
 * whichever call first happens to exercise the broken route.
 */
class RestUrpcConfigTest {

    private val client = HttpClient(MockEngine { respond("") })

    private fun buildTable(configure: RestUrpcConfig.() -> Unit) =
        client.restUrpcClient(baseUrl = "https://api.example.com", configure = configure)

    @Test
    fun mappingOneWireNameTwiceFailsWhenTheTableIsBuilt() {
        val failure = assertFailsWith<IllegalArgumentException> {
            buildTable {
                service("userService") {
                    "getUser" via get("/users/{id}")
                    "getUser" via post("/users/lookup")
                }
            }
        }

        val message = failure.message.orEmpty()
        assertTrue("userService.getUser" in message, message)
        assertTrue("GET /users/{id}" in message, message)
        assertTrue("POST /users/lookup" in message, message)
    }

    @Test
    fun mappingOneWireNameTwiceAcrossServiceBlocksAlsoFails() {
        assertFailsWith<IllegalArgumentException> {
            buildTable {
                service("userService") { "getUser" via get("/users/{id}") }
                service("userService") { "getUser" via get("/v2/users/{id}") }
            }
        }
    }

    @Test
    fun twoBlocksForOneServiceMergeWhenTheyDoNotCollide() {
        val urpc = buildTable {
            service("userService") { "getUser" via get("/users/{id}") }
            service("userService") { "listUsers" via get("/users") }
        }

        assertEquals(setOf("userService.getUser", "userService.listUsers"), urpc.mappedWireNames)
    }

    @Test
    fun anUnclosedPlaceholderFailsWhenTheTableIsBuilt() {
        val failure = assertFailsWith<IllegalArgumentException> {
            buildTable { service("userService") { "getUser" via get("/users/{id") } }
        }

        assertTrue("unclosed" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun anUnopenedPlaceholderFailsWhenTheTableIsBuilt() {
        val failure = assertFailsWith<IllegalArgumentException> {
            buildTable { service("userService") { "getUser" via get("/users/id}") } }
        }

        assertTrue("no matching" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun anEmptyPlaceholderFailsWhenTheTableIsBuilt() {
        val failure = assertFailsWith<IllegalArgumentException> {
            buildTable { service("userService") { "getUser" via get("/users/{}") } }
        }

        assertTrue("empty placeholder" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun aBlankServicePrefixFails() {
        assertFailsWith<IllegalArgumentException> {
            buildTable { service("  ") { "getUser" via get("/users/{id}") } }
        }
    }

    @Test
    fun aBlankFunctionNameFails() {
        assertFailsWith<IllegalArgumentException> {
            buildTable { service("userService") { "" via get("/users") } }
        }
    }

    @Test
    fun aPathWithoutALeadingSlashIsNormalisedRatherThanRejected() {
        val urpc = buildTable { service("userService") { "listUsers" via get("users") } }

        assertEquals(setOf("userService.listUsers"), urpc.mappedWireNames)
    }
}
