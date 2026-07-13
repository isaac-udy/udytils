package dev.isaacudy.udytils.urpc.client

import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceException
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcCallContext
import dev.isaacudy.udytils.urpc.UrpcCallKind
import dev.isaacudy.udytils.urpc.UrpcClientInterceptor
import dev.isaacudy.udytils.urpc.UrpcFrame
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/** Thrown into a call's flow when the shared connection drops and the call can't be resumed. */
class UrpcConnectionClosedException : RuntimeException("urpc connection closed")

/**
 * The raw single-connection transport that [UrpcConnection] drives. One invocation of [run]
 * == one live socket: it connects, pumps frames from [outgoing] to the peer and decoded peer
 * frames into [incoming], and suspends until the connection closes (returns) or fails (throws).
 * It must NOT close [incoming]/[outgoing] — the manager owns their lifetime.
 *
 * The transport is unauthenticated: auth is per-call (carried in each [UrpcFrame.Open]'s
 * metadata by an interceptor), so there is no connect-time token.
 *
 * Abstracted so [UrpcConnection]'s multiplexing / reconnect / backpressure logic can be unit
 * tested with an in-memory fake, with no Ktor or server involved.
 */
interface UrpcConnectionTransport {
    suspend fun run(
        outgoing: ReceiveChannel<UrpcFrame>,
        incoming: SendChannel<UrpcFrame>,
    )
}

/**
 * Multiplexes every streaming and bidirectional urpc call over a single connection.
 *
 * Each call is a logical channel keyed by a per-connection [UrpcFrame.callId]; the connection is
 * opened lazily once there is ≥1 active call and reconnected with exponential backoff if it drops.
 * Server-streaming calls are re-`Open`ed on reconnect (an idempotent replay of the request);
 * bidirectional calls are fail-loud (no resume). One call's terminal `Error`/`Complete` ends only
 * that call — the shared socket stays up for the others.
 *
 * Auth and other per-call concerns live in [interceptors], which run when a call is opened. An
 * interceptor may **suspend** to gate a call (e.g. wait until authenticated): because a call is
 * only registered — and the socket only opened — once its interceptors complete, a gated call
 * makes no connection (so a logged-out client never opens a streaming socket). Interceptors
 * populate [UrpcCallContext.metadata], which is sent in the call's `Open` frame.
 *
 * Note: a call's metadata is captured when it is first opened and replayed verbatim on reconnect.
 * A token refreshed mid-connection is therefore not re-applied to an already-open call until it
 * re-opens with fresh interceptor output — acceptable because reconnects are brief and logout
 * tears calls down (cancelling their flows) rather than relying on the connection to re-gate.
 *
 * Unary calls do NOT go through here — they stay on plain HTTP in the owning factory (which runs
 * the same interceptor chain to populate request headers).
 */
internal class UrpcConnection(
    private val scope: CoroutineScope,
    private val transport: UrpcConnectionTransport,
    private val interceptors: List<UrpcClientInterceptor>,
    private val logger: UrpcLogger,
) {
    private val mutex = Mutex()
    private var nextCallId = 0L
    private val calls = mutableMapOf<Long, CallHandle>()

    /**
     * The live connection's outgoing channel, or null while disconnected. A StateFlow (rather
     * than a plain var) so [sendClient] can *wait* for a connection instead of dropping frames:
     * a bidirectional call's request flow starts pumping as soon as the call registers, which on
     * a cold factory races the very first connect — frames sent in that window used to be
     * silently lost (the server saw the `Open` but never the `ClientData`, hanging the call).
     *
     * Mutated only under [mutex], and always set *after* the connection's `Open` replays are
     * enqueued, so an awakened sender can never enqueue `ClientData` ahead of its call's `Open`.
     */
    private val outgoingState = MutableStateFlow<SendChannel<UrpcFrame>?>(null)
    private var supervisorStarted = false

    /** Drives connect/disconnect: true while there is ≥1 active call. */
    private val activeCalls = MutableStateFlow(false)

    /** A streaming call: re-opened on reconnect (idempotent request replay). */
    fun <Req, Res> openStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        request: Req,
    ): Flow<Res> = flow {
        val metadata = buildMetadata(descriptor.name, UrpcCallKind.SERVER_STREAMING)
        val payload =
            if (descriptor.isUnitRequest) null
            else serviceFunctionJson.encodeToJsonElement(descriptor.requestSerializer, request)
        val channel = Channel<Res>(CALL_BUFFER)
        val callId = register { id ->
            StreamingCallHandle(id, descriptor.name, payload, metadata, descriptor.responseSerializer, channel)
        }
        try {
            for (value in channel) emit(value)
        } finally {
            unregister(callId)
            channel.cancel()
        }
    }

    /** A bidirectional call: requests stream as ClientData frames; fail-loud on disconnect. */
    fun <Req, Res> openBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        requests: Flow<Req>,
    ): Flow<Res> = flow {
        val metadata = buildMetadata(descriptor.name, UrpcCallKind.BIDIRECTIONAL)
        val channel = Channel<Res>(CALL_BUFFER)
        val callId = register { id ->
            BidiCallHandle(id, descriptor.name, metadata, descriptor.responseSerializer, channel)
        }
        coroutineScope {
            val sender = launch {
                try {
                    requests.collect { req ->
                        sendClient(UrpcFrame.ClientData(callId, serviceFunctionJson.encodeToJsonElement(descriptor.requestSerializer, req)))
                    }
                    sendClient(UrpcFrame.ClientComplete(callId))
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger.warn("urpc: request flow failed for ${descriptor.name}: ${t.message}", t)
                }
            }
            try {
                for (value in channel) emit(value)
            } finally {
                sender.cancel()
                unregister(callId)
                channel.cancel()
            }
        }
    }

    /** Runs the interceptor chain (which may suspend to gate the call) and snapshots the metadata. */
    private suspend fun buildMetadata(wireName: String, kind: UrpcCallKind): Map<String, String> {
        val context = UrpcCallContext(wireName, kind)
        interceptors.forEach { it.interceptOpen(context) }
        return context.metadata.toMap()
    }

    private suspend fun register(build: (Long) -> CallHandle): Long = mutex.withLock {
        if (!supervisorStarted) {
            supervisorStarted = true
            scope.launch { supervise() }
        }
        val id = ++nextCallId
        val handle = build(id)
        calls[id] = handle
        activeCalls.value = true
        // If a connection is already live, open immediately; otherwise the supervisor will
        // connect (activeCalls just went true) and re-open every registered call.
        outgoingState.value?.trySend(handle.openFrame())
        id
    }

    private suspend fun unregister(callId: Long) {
        mutex.withLock {
            if (calls.remove(callId) != null) {
                outgoingState.value?.trySend(UrpcFrame.Cancel(callId))
            }
            // Recompute unconditionally: the demux may already have removed this call on a
            // server `Complete`/`Error`, and the last removal must still drive teardown.
            activeCalls.value = calls.isNotEmpty()
        }
    }

    /** Removes a call and recomputes the connect/teardown signal; the handle is returned so
     *  terminal work (complete/fail) runs outside the lock. */
    private suspend fun removeCall(callId: Long): CallHandle? = mutex.withLock {
        calls.remove(callId)?.also { activeCalls.value = calls.isNotEmpty() }
    }

    private suspend fun sendClient(frame: UrpcFrame) {
        // Wait for a live connection rather than dropping the frame: a bidirectional sender can
        // race ahead of the factory's first connect, and anything sent in that window would be
        // lost for good (the call's `Open` replays on connect; its `ClientData` does not).
        // Sending OUTSIDE the mutex keeps real backpressure on the request flow without holding
        // the manager lock while suspended. If the connection dies instead, either this call is
        // failed as unresumable (bidirectional) or the send below fails on the closed channel.
        val out = outgoingState.filterNotNull().first()
        try {
            out.send(frame)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Socket dropped between the snapshot and the send — the call fails/re-opens via the manager.
            logger.debug("urpc: send dropped for call ${frame.callId}: ${t.message}")
        }
    }

    // --- connection supervisor ---

    private suspend fun supervise() {
        // collectLatest cancels the connect loop (and any live socket) when activeCalls goes
        // false — the last call ended — and restarts it when a call appears again.
        activeCalls.collectLatest { active ->
            if (!active) return@collectLatest
            var delayMs = INITIAL_RECONNECT_DELAY
            while (currentCoroutineContext().isActive) {
                try {
                    runConnection()
                    delayMs = INITIAL_RECONNECT_DELAY // closed after a healthy session — reset backoff
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger.warn("urpc connection error: ${t.message}", t)
                }
                if (!currentCoroutineContext().isActive) break
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY)
            }
        }
    }

    private suspend fun runConnection() {
        val outgoing = Channel<UrpcFrame>(Channel.BUFFERED)
        val incoming = Channel<UrpcFrame>(INCOMING_BUFFER)
        mutex.withLock {
            calls.values.forEach { outgoing.trySend(it.openFrame()) } // (re)open every active call
            // Published only after the Opens are enqueued: a sender woken by this state change
            // must not get its ClientData onto the wire ahead of its call's Open.
            outgoingState.value = outgoing
        }
        try {
            coroutineScope {
                val demux = launch { for (frame in incoming) handleServerFrame(frame) }
                try {
                    transport.run(outgoing, incoming)
                } finally {
                    demux.cancel()
                }
            }
        } finally {
            // Clear the live-connection pointer and fail any call that can't survive a reconnect
            // (bidirectional). Reopenable (streaming) calls stay registered to be re-opened.
            val orphanedBidi = mutex.withLock {
                if (outgoingState.value === outgoing) outgoingState.value = null
                calls.values.filterNot { it.reopenable }.onEach { calls.remove(it.callId) }
            }
            orphanedBidi.forEach { it.fail(UrpcConnectionClosedException()) }
            if (orphanedBidi.isNotEmpty()) mutex.withLock { activeCalls.value = calls.isNotEmpty() }
            incoming.close()
            outgoing.close()
        }
    }

    private suspend fun handleServerFrame(frame: UrpcFrame) {
        when (frame) {
            is UrpcFrame.Data -> {
                val handle = mutex.withLock { calls[frame.callId] }
                if (handle != null) {
                    try {
                        handle.deliver(frame.payload)
                    } catch (t: Throwable) {
                        // A consumer that cancels its flow cancels its per-call channel, and
                        // `send` on a cancelled channel throws CancellationException — so we must
                        // NOT blindly rethrow it, or one consumer's cancellation would silently
                        // kill this shared demux loop and stall every other call. Only propagate
                        // when our OWN coroutine is being cancelled (the socket is closing).
                        if (!currentCoroutineContext().isActive) throw t
                        logger.debug("urpc: dropped frame for call ${frame.callId}: ${t.message}")
                    }
                }
            }

            // Complete/Error removals recompute activeCalls just like unregister does: when the
            // LAST call ends via a server frame, the consumer's later unregister finds nothing to
            // remove — without the recompute here the manager would keep the socket (and its
            // reconnect loop) alive forever.
            is UrpcFrame.Complete -> {
                removeCall(frame.callId)?.complete()
            }

            is UrpcFrame.Error -> {
                removeCall(frame.callId)?.fail(
                    ServiceException(
                        statusCode = frame.statusCode,
                        errorType = frame.error.type,
                        errorMessage = frame.error.message
                            ?: ErrorMessage(title = "Streaming Error", message = "An unknown error occurred"),
                    ),
                )
            }

            // Open / Cancel / ClientData / ClientComplete / Auth are client→server only.
            else -> logger.warn("urpc: ignoring unexpected server frame ${frame::class.simpleName}")
        }
    }

    private interface CallHandle {
        val callId: Long
        val reopenable: Boolean
        fun openFrame(): UrpcFrame.Open
        suspend fun deliver(payload: JsonElement)
        fun complete()
        fun fail(error: Throwable)
    }

    private class StreamingCallHandle<Res>(
        override val callId: Long,
        private val wireName: String,
        private val payload: JsonElement?,
        private val metadata: Map<String, String>,
        private val serializer: KSerializer<Res>,
        private val output: SendChannel<Res>,
    ) : CallHandle {
        override val reopenable get() = true
        override fun openFrame() = UrpcFrame.Open(callId, wireName, payload, metadata)
        override suspend fun deliver(payload: JsonElement) =
            output.send(serviceFunctionJson.decodeFromJsonElement(serializer, payload))
        override fun complete() { output.close() }
        override fun fail(error: Throwable) { output.close(error) }
    }

    private class BidiCallHandle<Res>(
        override val callId: Long,
        private val wireName: String,
        private val metadata: Map<String, String>,
        private val serializer: KSerializer<Res>,
        private val output: SendChannel<Res>,
    ) : CallHandle {
        override val reopenable get() = false
        override fun openFrame() = UrpcFrame.Open(callId, wireName, payload = null, metadata = metadata)
        override suspend fun deliver(payload: JsonElement) =
            output.send(serviceFunctionJson.decodeFromJsonElement(serializer, payload))
        override fun complete() { output.close() }
        override fun fail(error: Throwable) { output.close(error) }
    }

    private companion object {
        const val CALL_BUFFER = 64
        const val INCOMING_BUFFER = 64
        const val INITIAL_RECONNECT_DELAY = 1_000L
        const val MAX_RECONNECT_DELAY = 30_000L
    }
}
