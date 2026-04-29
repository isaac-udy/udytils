package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.client.urpcClient
import dev.isaacudy.udytils.urpc.server.urpc
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ExampleServiceRoundTripTest {

    private class ExampleServiceImpl : ExampleService {
        override suspend fun sayHello(request: SayHelloRequest): SayHelloResponse =
            SayHelloResponse(greeting = "Hello, ${request.name}!")

        override suspend fun ping(): PongResponse =
            PongResponse(message = "pong")
    }

    @Test
    fun unaryCallRoundTripsThroughGeneratedClientAndServer() = testApplication {
        application {
            routing {
                urpc {
                    install(ExampleServiceImpl())
                }
            }
        }
        val httpClient = createClient { /* default config */ }
        val urpc = httpClient.urpcClient(baseUrl = "")
        val service = urpc.create<ExampleService>()

        val helloResponse = service.sayHello(SayHelloRequest(name = "Isaac"))
        assertEquals("Hello, Isaac!", helloResponse.greeting)

        val pingResponse = service.ping()
        assertEquals("pong", pingResponse.message)
    }
}
