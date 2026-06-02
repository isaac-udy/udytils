@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.urpc.ServiceError
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcClientInterceptor
import dev.isaacudy.udytils.urpc.UrpcFrame
import dev.isaacudy.udytils.urpc.UrpcLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
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
}
