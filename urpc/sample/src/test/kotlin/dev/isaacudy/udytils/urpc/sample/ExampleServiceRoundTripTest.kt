package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.UrpcService
import dev.isaacudy.udytils.urpc.client.urpcClient
import dev.isaacudy.udytils.urpc.server.applicationCall
import dev.isaacudy.udytils.urpc.server.urpc
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install as installApplicationPlugin
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
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

    /**
     * Wires up urpc with a hand-rolled service list (no DI). The user's lambda
     * resolves the matching binding by `accepts(call)` — same shape we expect
     * a real Koin-backed consumer to use, just with the lookup pointed at a
     * local list instead of `call.scope.getAll<UrpcService>()`.
     */
    private fun Application.installUrpcWithFakeImpl() {
        installApplicationPlugin(ServerWebSockets)
        val services: List<UrpcService> = listOf(ExampleServiceUrpcBinding(ExampleServiceImpl()))
        routing {
            urpc { call ->
                val service = services.firstOrNull { it.accepts(call) }
                if (service == null) {
                    call.applicationCall.respond(HttpStatusCode.NotFound)
                    return@urpc
                }
                service.handle(call)
            }
        }
    }

    @Test
    fun unaryCallsRoundTripThroughGeneratedClientAndServer() = testApplication {
        application { installUrpcWithFakeImpl() }
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
        application { installUrpcWithFakeImpl() }
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
        application { installUrpcWithFakeImpl() }
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
        application { installUrpcWithFakeImpl() }
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
        application { installUrpcWithFakeImpl() }
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
        application { installUrpcWithFakeImpl() }
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

    @Test
    fun handlerFailureOnOneCallDoesNotKillOtherMultiplexedCalls() = testApplication {
        application {
            installApplicationPlugin(ServerWebSockets)
            val binding = ExampleServiceUrpcBinding(ExampleServiceImpl())
            routing {
                urpc { call ->
                    // Simulate a handler-level failure (e.g. DI resolution blowing up) for one
                    // call. With per-call isolation it must NOT take down the shared socket or the
                    // concurrent healthy call below.
                    if (call.wireName == ExampleServiceDescriptors.ambiguousStream.name) {
                        throw RuntimeException("boom: handler failed before dispatch")
                    }
                    binding.handle(call)
                }
            }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        coroutineScope {
            // One call whose handler throws, and one healthy streaming call sharing the same socket.
            val failing = async { runCatching { service.ambiguousStream(AmbiguousStreamRequest(count = 2)).toList() } }
            val ticks = service.countdown(CountdownRequest(from = 2)).toList()

            // The healthy call completed — the socket survived the other call's handler failure.
            assertEquals(
                listOf(CountdownTick(2), CountdownTick(1), CountdownTick(0)),
                ticks,
            )
            // And the failing call surfaced an error rather than hanging or silently vanishing.
            assertTrue(failing.await().isFailure)
        }
    }
}
