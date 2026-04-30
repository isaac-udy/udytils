package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.ServiceException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

        override fun ambiguousStream(request: AmbiguousStreamRequest): Flow<AmbiguousResponse> = flow {
            // Mix payloads that DO populate the user's `error` field with payloads
            // that don't. The wire envelope should treat all of them as data.
            repeat(request.count) { i ->
                if (i % 2 == 0) {
                    emit(AmbiguousResponse(error = null, data = "ok-$i"))
                } else {
                    emit(AmbiguousResponse(error = ErrorDetail("E$i", "domain-error-$i"), data = "with-error-$i"))
                }
            }
        }

        override fun failingStream(request: FailingStreamRequest): Flow<EchoMessage> = flow {
            repeat(request.emitBeforeFailing) { i -> emit(EchoMessage("ok-$i")) }
            throw IllegalArgumentException("server gave up after ${request.emitBeforeFailing} emissions")
        }

        override suspend fun functionThatGotRenamedInSource(request: RenamedRequest): RenamedResponse =
            RenamedResponse(echoedPayload = "echoed:${request.payload}")
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

        // No take(N) needed — the server's countdown flow completes after emitting
        // CountdownTick(0), and the server emits a UrpcStreamingFrame.Complete that
        // ends the client-side flow gracefully.
        val ticks = service.countdown(CountdownRequest(from = 3)).toList()

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

    @Test
    fun urpcWireNameOverrideIsHonoredByGeneratedDescriptor() = testApplication {
        application {
            installApplicationPlugin(ServerWebSockets)
            routing { urpc { install(ExampleServiceImpl()) } }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        // Direct check: the processor honored @UrpcWireName when generating the
        // descriptor. If a future change silently dropped the override, this
        // assertion fails up front instead of leaving the regression to drift.
        assertEquals(
            "example.renamed_for_wire_compat",
            ExampleServiceDescriptors.functionThatGotRenamedInSource.name,
        )

        // Round-trip check: client and server agree on the overridden wire path.
        // (If they disagreed, the call would 404; matching descriptors is the
        // only way this passes end-to-end.)
        val response = service.functionThatGotRenamedInSource(RenamedRequest("hi"))
        assertEquals("echoed:hi", response.echoedPayload)
    }

    @Test
    fun streamingResponsesWithTopLevelErrorFieldsAreNotMisinterpreted() = testApplication {
        application {
            installApplicationPlugin(ServerWebSockets)
            routing { urpc { install(ExampleServiceImpl()) } }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        val emissions = service.ambiguousStream(AmbiguousStreamRequest(count = 4)).toList()

        // All four should arrive as `Data` payloads — including the ones whose
        // `error` field is populated. Pre-envelope code would have thrown a
        // `ServiceException` on the second and fourth.
        assertEquals(
            listOf(
                AmbiguousResponse(error = null, data = "ok-0"),
                AmbiguousResponse(error = ErrorDetail("E1", "domain-error-1"), data = "with-error-1"),
                AmbiguousResponse(error = null, data = "ok-2"),
                AmbiguousResponse(error = ErrorDetail("E3", "domain-error-3"), data = "with-error-3"),
            ),
            emissions,
        )
    }

    @Test
    fun streamingErrorIsDeliveredViaTypedEnvelopeAfterPartialEmissions() = testApplication {
        application {
            installApplicationPlugin(ServerWebSockets)
            routing { urpc { install(ExampleServiceImpl()) } }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        val received = mutableListOf<EchoMessage>()
        val thrown = assertFailsWith<ServiceException> {
            service.failingStream(FailingStreamRequest(emitBeforeFailing = 2))
                .toList(received)
        }

        assertEquals(
            listOf(EchoMessage("ok-0"), EchoMessage("ok-1")),
            received,
        )
        assertEquals(400, thrown.statusCode)
        assertEquals("IllegalArgumentException", thrown.errorType)
        assertTrue(
            thrown.message!!.contains("Unexpected error") || thrown.message!!.contains("server gave up"),
            "Unexpected error title: ${thrown.message}",
        )
    }
}
