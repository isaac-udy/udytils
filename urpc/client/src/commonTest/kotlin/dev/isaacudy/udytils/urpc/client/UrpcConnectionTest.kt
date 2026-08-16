@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcClientInterceptor
import dev.isaacudy.udytils.urpc.UrpcFrame
import dev.isaacudy.udytils.urpc.UrpcLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [UrpcConnection]'s multiplexing core against an in-memory [FakeTransport] — no Ktor,
 * no server. Uses [UnconfinedTestDispatcher] so the connection's channel-handoff coroutines
 * (demux ↔ per-call flows) run eagerly. Delivered values are asserted by suspending on a
 * channel `receive()` (which lets `runTest` advance until delivery completes) rather than by
 * driving the scheduler by hand, which is brittle across the reconnect path.
 */
class UrpcConnectionTest {

    private fun strDesc(name: String) = StreamingServiceDescriptor(
        name = name,
        requestSerializer = String.serializer(),
        responseSerializer = String.serializer(),
        isUnitRequest = false,
    )

    private fun bidiDesc(name: String) = BidirectionalServiceDescriptor(
        name = name,
        requestSerializer = String.serializer(),
        responseSerializer = String.serializer(),
    )

    /** Captures what the manager sends, lets the test push server frames + simulate drops. */
    private class FakeTransport : UrpcConnectionTransport {
        val connections = Channel<Conn>(Channel.UNLIMITED)

        class Conn(
            val outgoing: ReceiveChannel<UrpcFrame>,
            val incoming: SendChannel<UrpcFrame>,
        ) {
            private val ended = CompletableDeferred<Unit>()
            suspend fun await() = ended.await()
            fun drop() { ended.complete(Unit) }
        }

        override suspend fun run(outgoing: ReceiveChannel<UrpcFrame>, incoming: SendChannel<UrpcFrame>) {
            val conn = Conn(outgoing, incoming)
            connections.send(conn)
            conn.await()
        }
    }

    private fun ReceiveChannel<UrpcFrame>.drainOpens(): List<UrpcFrame.Open> =
        generateSequence { tryReceive().getOrNull() }.filterIsInstance<UrpcFrame.Open>().toList()

    private fun ReceiveChannel<UrpcFrame>.drain(): List<UrpcFrame> =
        generateSequence { tryReceive().getOrNull() }.toList()

    private fun newConnection(
        scope: CoroutineScope,
        transport: FakeTransport,
        interceptors: List<UrpcClientInterceptor> = emptyList(),
    ) = UrpcConnection(
        scope = scope,
        transport = transport,
        interceptors = interceptors,
        logger = UrpcLogger.NoOp,
    )

    @Test
    fun demultiplexes_concurrent_calls_by_callId() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(backgroundScope, transport)
        val a = Channel<String>(Channel.UNLIMITED)
        val b = Channel<String>(Channel.UNLIMITED)
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "ra").collect { a.send(it) } }
        backgroundScope.launch { conn.openStreaming(strDesc("svc.b"), "rb").collect { b.send(it) } }
        advanceUntilIdle()

        val c = transport.connections.receive()
        val opens = c.outgoing.drainOpens()
        val idA = opens.first { it.wireName == "svc.a" }.callId
        val idB = opens.first { it.wireName == "svc.b" }.callId

        c.incoming.send(UrpcFrame.Data(idA, JsonPrimitive("a1")))
        c.incoming.send(UrpcFrame.Data(idB, JsonPrimitive("b1")))
        c.incoming.send(UrpcFrame.Data(idA, JsonPrimitive("a2")))

        assertEquals("a1", a.receive())
        assertEquals("a2", a.receive())
        assertEquals("b1", b.receive())
        // One shared connection served both calls.
        assertTrue(transport.connections.tryReceive().isFailure)
    }

    @Test
    fun complete_ends_only_that_call() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(backgroundScope, transport)
        val aDone = CompletableDeferred<Unit>()
        val b = Channel<String>(Channel.UNLIMITED)
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { }; aDone.complete(Unit) }
        backgroundScope.launch { conn.openStreaming(strDesc("svc.b"), "r").collect { b.send(it) } }
        advanceUntilIdle()

        val c = transport.connections.receive()
        val opens = c.outgoing.drainOpens()
        val idA = opens.first { it.wireName == "svc.a" }.callId
        val idB = opens.first { it.wireName == "svc.b" }.callId

        c.incoming.send(UrpcFrame.Complete(idA))
        c.incoming.send(UrpcFrame.Data(idB, JsonPrimitive("b1")))

        aDone.await() // svc.a completed
        assertEquals("b1", b.receive()) // svc.b unaffected
    }

    @Test
    fun error_fails_only_that_call_and_keeps_connection() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(backgroundScope, transport)
        val aError = CompletableDeferred<Throwable>()
        val b = Channel<String>(Channel.UNLIMITED)
        backgroundScope.launch {
            try {
                conn.openStreaming(strDesc("svc.a"), "r").collect { }
            } catch (t: Throwable) {
                aError.complete(t)
            }
        }
        backgroundScope.launch { conn.openStreaming(strDesc("svc.b"), "r").collect { b.send(it) } }
        advanceUntilIdle()

        val c = transport.connections.receive()
        val opens = c.outgoing.drainOpens()
        val idA = opens.first { it.wireName == "svc.a" }.callId
        val idB = opens.first { it.wireName == "svc.b" }.callId

        c.incoming.send(UrpcFrame.Error(idA, ServiceError(type = "Boom", message = null), statusCode = 418))
        c.incoming.send(UrpcFrame.Data(idB, JsonPrimitive("ok")))

        val error = aError.await()
        assertTrue(error is ServiceException, "expected ServiceException, got $error")
        assertEquals(418, error.statusCode)
        assertEquals("ok", b.receive()) // svc.b unaffected
        // The shared connection survived one call's error.
        assertTrue(transport.connections.tryReceive().isFailure)
    }

    @Test
    fun cancelling_collector_sends_cancel_frame() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(backgroundScope, transport)
        val job = backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { } }
        advanceUntilIdle()

        val c = transport.connections.receive()
        val open = c.outgoing.drainOpens().single()

        job.cancel()
        advanceUntilIdle()

        val cancels = c.outgoing.drain().filterIsInstance<UrpcFrame.Cancel>()
        assertTrue(cancels.any { it.callId == open.callId }, "expected a Cancel for call ${open.callId}")
    }

    @Test
    fun reconnect_reopens_active_streaming_call() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(backgroundScope, transport)
        val received = Channel<String>(Channel.UNLIMITED)
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { received.send(it) } }
        advanceUntilIdle()

        val c1 = transport.connections.receive()
        val open1 = c1.outgoing.drainOpens().single()
        c1.drop() // socket dropped without a Complete
        advanceUntilIdle() // backoff delay elapses under virtual time

        val c2 = transport.connections.receive()
        val open2 = c2.outgoing.drainOpens().single()
        assertEquals(open1.wireName, open2.wireName)
        assertEquals(open1.callId, open2.callId) // same logical call, re-opened

        c2.incoming.send(UrpcFrame.Data(open2.callId, JsonPrimitive("after-reconnect")))
        assertEquals("after-reconnect", received.receive())
    }

    @Test
    fun suspending_interceptor_gates_the_connection() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val gate = CompletableDeferred<Unit>()
        // Interceptor suspends until "logged in" — gating the call before it registers.
        val conn = newConnection(backgroundScope, transport, listOf(UrpcClientInterceptor { gate.await() }))
        val received = Channel<String>(Channel.UNLIMITED)
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { received.send(it) } }
        advanceUntilIdle()

        // Interceptor is still suspended → call never registered → no socket opened.
        assertTrue(
            transport.connections.tryReceive().isFailure,
            "no connection should open while the interceptor gates the call",
        )

        gate.complete(Unit) // "log in"
        advanceUntilIdle()

        val c = transport.connections.receive()
        val open = c.outgoing.drainOpens().single()
        c.incoming.send(UrpcFrame.Data(open.callId, JsonPrimitive("hi")))
        assertEquals("hi", received.receive())
    }

    @Test
    fun bidirectional_requests_sent_before_first_connect_are_not_dropped() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        // The manager's supervisor runs on a QUEUED dispatcher while the collector (and therefore
        // the bidirectional request sender) runs unconfined — so the sender pumps its requests
        // before the first connection exists. This is the cold-factory race that used to drop
        // every pre-connect ClientData frame (the server saw the Open but no data — a hung call).
        val connectionScope = CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler))
        val conn = newConnection(connectionScope, transport)

        backgroundScope.launch {
            conn.openBidirectional(
                bidiDesc("svc.echo"),
                flow {
                    emit("a")
                    emit("b")
                    emit("c")
                    awaitCancellation() // consumer-driven call: keep the request side open
                },
            ).collect { }
        }
        advanceUntilIdle() // now let the supervisor connect

        val c = transport.connections.receive()
        val frames = c.outgoing.drain()
        assertTrue(frames.first() is UrpcFrame.Open, "Open must reach the wire first, got $frames")
        assertEquals(
            listOf("a", "b", "c"),
            frames.filterIsInstance<UrpcFrame.ClientData>().map { (it.payload as JsonPrimitive).content },
            "requests sent before the first connect must be delivered once it is up",
        )
    }

    @Test
    fun last_call_completing_via_server_frame_tears_down_the_connection() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(backgroundScope, transport)
        val done = CompletableDeferred<Unit>()
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { }; done.complete(Unit) }
        advanceUntilIdle()

        val c = transport.connections.receive()
        val open = c.outgoing.drainOpens().single()
        c.incoming.send(UrpcFrame.Complete(open.callId))
        done.await()
        // Advance past the linger window (debounce delays the false→teardown signal)
        testScheduler.advanceTimeBy(UrpcConnection.LINGER_MS + 1000)
        advanceUntilIdle()

        // After the linger window, the connection tears down.
        c.outgoing.drain()
        assertTrue(c.outgoing.isClosedForReceive, "manager should close the connection once no calls remain")
        assertTrue(transport.connections.tryReceive().isFailure, "no reconnect should follow teardown")
    }

    @Test
    fun interceptor_metadata_is_sent_in_open_frame() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(
            backgroundScope,
            transport,
            listOf(UrpcClientInterceptor { it.metadata["authorization"] = "Bearer xyz" }),
        )
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { } }
        advanceUntilIdle()

        val c = transport.connections.receive()
        val open = c.outgoing.drainOpens().single()
        assertEquals("Bearer xyz", open.metadata["authorization"])
    }

    @Test
    fun linger_keeps_connection_alive_for_a_new_call() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(backgroundScope, transport)
        val aDone = CompletableDeferred<Unit>()
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { }; aDone.complete(Unit) }
        advanceUntilIdle()

        val c = transport.connections.receive()
        val openA = c.outgoing.drainOpens().single()

        c.incoming.send(UrpcFrame.Complete(openA.callId))
        aDone.await()

        // Open call B during the linger window (before LINGER_MS elapses)
        val b = Channel<String>(Channel.UNLIMITED)
        backgroundScope.launch { conn.openStreaming(strDesc("svc.b"), "r").collect { b.send(it) } }
        advanceUntilIdle()

        assertTrue(transport.connections.tryReceive().isFailure, "should reuse the lingered connection")
        val openB = c.outgoing.drainOpens().first { it.wireName == "svc.b" }
        c.incoming.send(UrpcFrame.Data(openB.callId, JsonPrimitive("via-linger")))
        assertEquals("via-linger", b.receive())
    }

    @Test
    fun reconnect_uses_fresh_interceptor_metadata() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        var token = "token-1"
        val conn = newConnection(
            backgroundScope, transport,
            listOf(UrpcClientInterceptor { it.metadata["authorization"] = "Bearer $token" }),
        )
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { } }
        advanceUntilIdle()

        val c1 = transport.connections.receive()
        val open1 = c1.outgoing.drainOpens().single()
        assertEquals("Bearer token-1", open1.metadata["authorization"])

        token = "token-2"
        c1.drop()
        advanceUntilIdle()

        val c2 = transport.connections.receive()
        val open2 = c2.outgoing.drainOpens().single()
        assertEquals(
            "Bearer token-2", open2.metadata["authorization"],
            "reconnect should re-run interceptors to pick up the refreshed token",
        )
    }

    @Test
    fun unhealthy_connection_does_not_reset_backoff() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport()
        val conn = newConnection(backgroundScope, transport)
        backgroundScope.launch { conn.openStreaming(strDesc("svc.a"), "r").collect { } }
        advanceUntilIdle()

        val c1 = transport.connections.receive()
        c1.outgoing.drain()
        val t0 = testScheduler.currentTime
        c1.drop()
        advanceUntilIdle()

        val c2 = transport.connections.receive()
        c2.outgoing.drain()
        val delay1 = testScheduler.currentTime - t0

        val t1 = testScheduler.currentTime
        c2.drop()
        advanceUntilIdle()

        val c3 = transport.connections.receive()
        c3.outgoing.drain()
        val delay2 = testScheduler.currentTime - t1

        assertTrue(delay2 > delay1, "backoff should increase after unhealthy connections: $delay1 -> $delay2")
        assertEquals(UrpcConnection.INITIAL_RECONNECT_DELAY, delay1)
        assertEquals(UrpcConnection.INITIAL_RECONNECT_DELAY * 2, delay2)
    }
}
