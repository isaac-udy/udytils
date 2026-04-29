package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.client.urpcClient
import dev.isaacudy.udytils.urpc.server.urpc
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.application.install as installApplicationPlugin
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals

class ExampleServiceRoundTripTest {

    private class ExampleServiceImpl : ExampleService {
        override suspend fun sayHello(request: SayHelloRequest): SayHelloResponse =
            SayHelloResponse(greeting = "Hello, ${request.name}!")

        override suspend fun ping(): PongResponse =
            PongResponse(message = "pong")

        override fun countdown(request: CountdownRequest): Flow<CountdownTick> = flow {
            for (i in request.from downTo 0) {
                emit(CountdownTick(remaining = i))
            }
        }

        override fun echoStream(requests: Flow<EchoMessage>): Flow<EchoMessage> =
            requests.map { EchoMessage(text = "echo:${it.text}") }
    }

    @Test
    fun unaryCallsRoundTripThroughGeneratedClientAndServer() = testApplication {
        application {
            installApplicationPlugin(ServerWebSockets)
            routing { urpc { install(ExampleServiceImpl()) } }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        assertEquals(
            "Hello, Isaac!",
            service.sayHello(SayHelloRequest(name = "Isaac")).greeting,
        )
        assertEquals("pong", service.ping().message)
    }

    @Test
    fun serverStreamingRoundTripsEveryEmission() = testApplication {
        application {
            installApplicationPlugin(ServerWebSockets)
            routing { urpc { install(ExampleServiceImpl()) } }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        val ticks = service.countdown(CountdownRequest(from = 3))
            .take(4)
            .toList()

        assertEquals(
            listOf(
                CountdownTick(3),
                CountdownTick(2),
                CountdownTick(1),
                CountdownTick(0),
            ),
            ticks,
        )
    }

    @Test
    fun bidirectionalStreamingEchoesEveryRequest() = testApplication {
        application {
            installApplicationPlugin(ServerWebSockets)
            routing { urpc { install(ExampleServiceImpl()) } }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        // Bidirectional calls are consumer-driven: the call lives as long as the
        // consumer keeps reading. Wrap the request flow with awaitCancellation()
        // so the WS stays open long enough to receive every echo, then take(3)
        // cancels the whole thing once we have what we need.
        val received = service.echoStream(
            flow {
                emit(EchoMessage("a"))
                emit(EchoMessage("b"))
                emit(EchoMessage("c"))
                awaitCancellation()
            },
        )
            .take(3)
            .toList()

        assertEquals(
            listOf(EchoMessage("echo:a"), EchoMessage("echo:b"), EchoMessage("echo:c")),
            received,
        )
    }
}
