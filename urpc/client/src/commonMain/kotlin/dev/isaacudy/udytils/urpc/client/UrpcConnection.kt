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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.random.Random

/** Thrown into a call's flow when the shared connection drops and the call can't be resumed. */
class UrpcConnectionClosedException : RuntimeException("urpc connection closed")

/**
 * Thrown by a [UrpcConnectionTransport] when the server closed the connection with close
 * code 4008 (idle timeout). The connection manager treats this as a clean close and resets
 * backoff so the next reconnect is immediate.
 */
class UrpcIdleDisconnectException : RuntimeException("urpc server idle disconnect")

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
 * populate [UrpcCallContext.metadata], which is sent in the call's `Open` frame and re-resolved
 * on reconnect so a refreshed token is applied to re-opened calls.
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
    private var lastConnectionHealthy = false
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY

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
        // Run interceptors before register to gate the call (e.g. wait until authenticated).
        buildMetadata(descriptor.name, UrpcCallKind.SERVER_STREAMING)
        val payload =
            if (descriptor.isUnitRequest) null
            else serviceFunctionJson.encodeToJsonElement(descriptor.requestSerializer, request)
        val channel = Channel<Res>(CALL_BUFFER)
        val (callId, _) = register { id ->
            StreamingCallHandle(id, descriptor.name, UrpcCallKind.SERVER_STREAMING, payload, descriptor.responseSerializer, channel)
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
        // Run interceptors before register to gate the call (e.g. wait until authenticated).
        buildMetadata(descriptor.name, UrpcCallKind.BIDIRECTIONAL)
        val channel = Channel<Res>(CALL_BUFFER)
        val (callId, opened) = register { id ->
            BidiCallHandle(id, descriptor.name, UrpcCallKind.BIDIRECTIONAL, descriptor.responseSerializer, channel)
        }
        coroutineScope {
            val sender = launch {
                // Wait for the Open frame to be enqueued before pumping ClientData, so the
                // wire always sees Open before any request payload on this call.
                opened.await()
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

    private suspend fun register(build: (Long) -> CallHandle): Pair<Long, CompletableDeferred<Unit>> {
        val (id, handle, out) = mutex.withLock {
            if (!supervisorStarted) {
                supervisorStarted = true
                scope.launch { supervise() }
            }
            val id = ++nextCallId
            val handle = build(id)
            calls[id] = handle
            activeCalls.value = true
            Triple(id, handle, outgoingState.value)
        }
        // Build metadata outside the lock: interceptors may suspend (e.g. bearer-token gating).
        // If a connection is already live, open immediately with fresh metadata; otherwise the
        // supervisor will connect (activeCalls just went true) and re-open every registered call.
        if (out != null) {
            val metadata = buildMetadata(handle.wireName, handle.callKind)
            out.trySend(handle.openFrame(metadata))
            handle.opened.complete(Unit)
        }
        return id to handle.opened
    }

    private suspend fun unregister(callId: Long) {
        mutex.withLock {
            if (calls.remove(callId) != null) {
                outgoingState.value?.trySend(UrpcFrame.Cancel(callId))
            }
            // Recompute unconditionally: the demux may already have removed this call on a
            // server Complete/Error, and the last removal must still drive teardown.
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

    @OptIn(FlowPreview::class)
    private suspend fun supervise() {
        // Debounce the false edge: when the last call ends activeCalls goes false immediately,
        // but the socket lingers for LINGER_MS before tearing down. A new call within the window
        // sets activeCalls back to true (0ms debounce), cancelling the pending false — the socket
        // survives and the new call reuses it. distinctUntilChanged suppresses the true→true
        // re-emission so collectLatest doesn't needlessly restart the connect loop.
        activeCalls
            .debounce { if (it) 0L else LINGER_MS }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (!active) return@collectLatest
                while (currentCoroutineContext().isActive) {
                    try {
                        runConnection()
                        if (lastConnectionHealthy) reconnectDelayMs = INITIAL_RECONNECT_DELAY
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: UrpcIdleDisconnectException) {
                        // Server closed with 4008 (idle): a present-but-idle user's streams
                        // should resume promptly, so reset backoff to INITIAL.
                        reconnectDelayMs = INITIAL_RECONNECT_DELAY
                    } catch (t: Throwable) {
                        logger.warn("urpc connection error: ${t.message}", t)
                    }
                    if (!currentCoroutineContext().isActive) break
                    val jitter = JITTER_MIN + Random.nextDouble() * (JITTER_MAX - JITTER_MIN)
                    delay((reconnectDelayMs * jitter).toLong())
                    reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY)
                }
            }
    }

    private suspend fun runConnection() {
        lastConnectionHealthy = false
        val outgoing = Channel<UrpcFrame>(Channel.BUFFERED)
        val incoming = Channel<UrpcFrame>(INCOMING_BUFFER)

        // Snapshot the active calls, then build metadata OUTSIDE the lock so a gating
        // interceptor (e.g. bearer-token waiting for login) never holds the mutex.
        val snapshot = mutex.withLock { calls.values.toList() }
        val openFrames = snapshot.map { handle ->
            val metadata = buildMetadata(handle.wireName, handle.callKind)
            handle.openFrame(metadata)
        }
        // Re-acquire: enqueue the Opens, then publish outgoingState. A sender woken by this
        // state change must not get its ClientData onto the wire ahead of its call's Open.
        // Also open any calls that registered between the snapshot and now: they saw out==null
        // (old connection's finally cleared it) and didn't self-open, and the snapshot missed them.
        val missed = mutex.withLock {
            snapshot.zip(openFrames).forEach { (handle, frame) ->
                outgoing.trySend(frame)
                handle.opened.complete(Unit)
            }
            outgoingState.value = outgoing
            calls.values.filter { c -> snapshot.none { it.callId == c.callId } }
        }
        for (handle in missed) {
            val metadata = buildMetadata(handle.wireName, handle.callKind)
            outgoing.trySend(handle.openFrame(metadata))
            handle.opened.complete(Unit)
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
                lastConnectionHealthy = true
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
            // remove — without the recompute here the manager would keep the socket alive forever.
            is UrpcFrame.Complete -> {
                lastConnectionHealthy = true
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
        val wireName: String
        val callKind: UrpcCallKind
        val reopenable: Boolean
        /** Completed after this call's Open frame is enqueued on the outgoing channel, so a
         *  bidirectional sender can wait for the Open before pumping ClientData/ClientComplete. */
        val opened: CompletableDeferred<Unit>
        fun openFrame(metadata: Map<String, String>): UrpcFrame.Open
        suspend fun deliver(payload: JsonElement)
        fun complete()
        fun fail(error: Throwable)
    }

    private class StreamingCallHandle<Res>(
        override val callId: Long,
        override val wireName: String,
        override val callKind: UrpcCallKind,
        private val payload: JsonElement?,
        private val serializer: KSerializer<Res>,
        private val output: SendChannel<Res>,
    ) : CallHandle {
        override val reopenable get() = true
        override val opened = CompletableDeferred<Unit>()
        override fun openFrame(metadata: Map<String, String>) = UrpcFrame.Open(callId, wireName, payload, metadata)
        override suspend fun deliver(payload: JsonElement) =
            output.send(serviceFunctionJson.decodeFromJsonElement(serializer, payload))
        override fun complete() { output.close() }
        override fun fail(error: Throwable) { output.close(error) }
    }

    private class BidiCallHandle<Res>(
        override val callId: Long,
        override val wireName: String,
        override val callKind: UrpcCallKind,
        private val serializer: KSerializer<Res>,
        private val output: SendChannel<Res>,
    ) : CallHandle {
        override val reopenable get() = false
        override val opened = CompletableDeferred<Unit>()
        override fun openFrame(metadata: Map<String, String>) = UrpcFrame.Open(callId, wireName, payload = null, metadata = metadata)
        override suspend fun deliver(payload: JsonElement) =
            output.send(serviceFunctionJson.decodeFromJsonElement(serializer, payload))
        override fun complete() { output.close() }
        override fun fail(error: Throwable) { output.close(error) }
    }

    internal companion object {
        const val CALL_BUFFER = 64
        const val INCOMING_BUFFER = 64
        const val INITIAL_RECONNECT_DELAY = 1_000L
        const val MAX_RECONNECT_DELAY = 30_000L
        const val LINGER_MS = 5_000L
        const val JITTER_MIN = 0.5
        const val JITTER_MAX = 1.5
    }
}
