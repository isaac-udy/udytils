package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FlowCacheTest {

    // ── Basic behavior ──────────────────────────────────────────────────────────

    /**
     * Basic usage: get returns values emitted by the upstream cold flow.
     */
    @Test
    fun `get returns values from the flow`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.INFINITE,
            replayTimeout = Duration.INFINITE
        )

        val result = cache.get("key") { flowOf(42) }.first()
        assertEquals(42, result)
    }

    /**
     * The provider block should only be called once for a given key when there are
     * active subscribers keeping the cache entry alive.
     */
    @Test
    fun `get reuses cached flow for the same key`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.INFINITE,
            replayTimeout = Duration.INFINITE
        )
        var createCount = 0

        val job1 = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()

        val job2 = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()

        assertEquals(1, createCount, "Provider should only be called once for the same key")

        job1.cancel()
        job2.cancel()
    }

    /**
     * Different keys should each get their own flow from their own provider call.
     */
    @Test
    fun `get creates separate entries for different keys`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.INFINITE,
            replayTimeout = Duration.INFINITE
        )

        val result1 = cache.get("key1") { flowOf(1) }.first()
        val result2 = cache.get("key2") { flowOf(2) }.first()

        assertEquals(1, result1)
        assertEquals(2, result2)
    }

    // ── Subscriber lifecycle ────────────────────────────────────────────────────

    /**
     * When the last subscriber cancels and both timeouts are zero, the cache entry
     * should be removed. A subsequent get should call the provider again.
     */
    @Test
    fun `cache entry is removed when all subscribers cancel`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.ZERO,
            replayTimeout = Duration.ZERO
        )
        var createCount = 0

        val job = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()
        assertEquals(1, createCount)

        job.cancel()
        runCurrent()

        // Cache should be empty now; a new get should invoke the provider again
        val job2 = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()
        assertEquals(2, createCount)

        job2.cancel()
    }

    /**
     * The cache entry should not be removed while there are still active subscribers,
     * even if some subscribers have cancelled.
     */
    @Test
    fun `cache entry persists while at least one subscriber is active`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.INFINITE,
            replayTimeout = Duration.INFINITE
        )
        var createCount = 0

        val job1 = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()

        val job2 = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()
        assertEquals(1, createCount, "Both subscribers should share the same entry")

        // Cancel only the first subscriber
        job1.cancel()
        runCurrent()

        // The entry should still be cached; a new subscriber should reuse it
        val job3 = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()
        assertEquals(1, createCount, "Entry should still be cached while job2 is active")

        job2.cancel()
        job3.cancel()
    }

    // ── Error handling ──────────────────────────────────────────────────────────

    /**
     * When a non-cancellation error occurs in the upstream flow, the cache entry should
     * be immediately removed. The error is propagated to subscribers.
     */
    @Test
    fun `cache entry is removed on upstream error`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.INFINITE,
            replayTimeout = Duration.INFINITE
        )
        var createCount = 0
        var flowBody: suspend FlowCollector<Int>.() -> Unit = {
            emit(1)
            throw RuntimeException("test error")
        }
        val cachedFlow = flow {
            flowBody.invoke(this)
        }

        val error = assertFailsWith<RuntimeException> {
            cache.get("key") {
                createCount++
                cachedFlow
            }.collect {}
        }
        assertEquals("test error", error.message)
        assertEquals(1, createCount)

        flowBody = { emit(99) }
        // Cache should be cleared; a new get should invoke the provider again
        val result = cache.get("key") { cachedFlow }.first()
        assertEquals(99, result)
        assertEquals(1, createCount)
    }

    /**
     * After an error removes a cache entry, a new get with the same key should
     * create a completely fresh flow via the provider.
     */
    @Test
    fun `fresh entry is created after error eviction`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.INFINITE,
            replayTimeout = Duration.INFINITE
        )
        var attempts = 0

        assertFailsWith<RuntimeException> {
            cache.get("key") {
                attempts++
                flow<Int> { throw RuntimeException("error") }
            }.first()
        }
        assertEquals(1, attempts)

        val result = cache.get("key") { attempts++; flowOf(42) }.first()
        assertEquals(42, result)
        assertEquals(2, attempts)
    }

    // ── Sharing behavior ────────────────────────────────────────────────────────

    /**
     * Multiple active subscribers should all receive values from the same underlying
     * upstream flow, demonstrating that the cold flow is shared.
     */
    @Test
    fun `multiple subscribers receive values from the same shared upstream`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.INFINITE,
            replayTimeout = Duration.INFINITE
        )
        val upstream = MutableSharedFlow<Int>()
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()

        val job1 = launch {
            cache.get("key") { upstream }.collect { values1.add(it) }
        }
        val job2 = launch {
            cache.get("key") { upstream }.collect { values2.add(it) }
        }
        runCurrent()

        upstream.emit(10)
        runCurrent()

        upstream.emit(20)
        runCurrent()

        assertEquals(listOf(10, 20), values1)
        assertEquals(listOf(10, 20), values2)

        job1.cancel()
        job2.cancel()
    }

    /**
     * The upstream cold flow should only be collected once, even with multiple subscribers.
     */
    @Test
    fun `upstream flow is only collected once for multiple subscribers`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.INFINITE,
            replayTimeout = Duration.INFINITE
        )
        var upstreamCollections = 0

        val job1 = launch {
            cache.get("key") {
                flow {
                    upstreamCollections++
                    awaitCancellation()
                }
            }.collect {}
        }
        runCurrent()

        val job2 = launch {
            cache.get("key") {
                flow {
                    upstreamCollections++
                    awaitCancellation()
                }
            }.collect {}
        }
        runCurrent()

        assertEquals(1, upstreamCollections, "Upstream should only be collected once")

        job1.cancel()
        job2.cancel()
    }

    // ── Subscription and replay timeouts ────────────────────────────────────────

    /**
     * When replayTimeout is configured, the last emitted value should be available
     * to a new subscriber even after the previous subscriber has cancelled and the
     * upstream has stopped, as long as the replay timeout has not expired.
     */
    @Test
    fun `retained value is emitted to new subscriber within replay timeout`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.ZERO,
            replayTimeout = 30.seconds
        )
        val upstream = MutableSharedFlow<Int>()

        // First subscriber: collect a value then cancel
        val values1 = mutableListOf<Int>()
        val job1 = launch {
            cache.get("key") { upstream }.collect { values1.add(it) }
        }
        runCurrent()

        upstream.emit(1)
        runCurrent()
        assertEquals(listOf(1), values1)

        job1.cancel()
        runCurrent()

        // Advance time within the replay timeout window
        advanceTimeBy(10.seconds.inWholeMilliseconds)

        // Second subscriber should get the retained value immediately
        val values2 = mutableListOf<Int>()
        val job2 = launch {
            cache.get("key") { upstream }.collect { values2.add(it) }
        }
        runCurrent()

        assertEquals(1, values2.firstOrNull(), "Retained value should be emitted immediately")

        job2.cancel()
    }

    /**
     * After all subscribers cancel and both the subscription and replay timeouts expire,
     * the cache entry should be fully removed. A new subscriber should create a fresh entry.
     */
    @Test
    fun `cache entry is removed after replay timeout expires`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.ZERO,
            replayTimeout = 10.seconds
        )
        var createCount = 0

        val job = launch {
            cache.get("key") {
                createCount++
                flow { emit(1); awaitCancellation() }
            }.collect {}
        }
        runCurrent()
        assertEquals(1, createCount)

        job.cancel()
        runCurrent()

        // Advance past the replay timeout
        advanceTimeBy(11.seconds.inWholeMilliseconds)
        runCurrent()

        // Cache should be empty; new get should create a fresh entry
        val job2 = launch {
            cache.get("key") {
                createCount++
                flow { emit(2); awaitCancellation() }
            }.collect {}
        }
        runCurrent()
        assertEquals(2, createCount, "Provider should be called again after replay timeout")

        job2.cancel()
    }

    /**
     * If a new subscriber arrives before the subscription timeout expires, the timeout
     * should be cancelled and the entry should be reused.
     */
    @Test
    fun `subscription timeout is cancelled when new subscriber arrives`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = 30.seconds,
            replayTimeout = Duration.ZERO
        )
        var createCount = 0

        val createCachedFlow = {
            createCount++
            flow {
                emit(1)
                delay(14.seconds)
                emit(2)
                awaitCancellation()
            }
        }

        val job1 = launch {
            cache.get("key") {
                createCachedFlow()
            }.collect {}
        }
        runCurrent()
        assertEquals(1, createCount)

        job1.cancel()
        runCurrent()

        // Advance partially into the subscription timeout window
        advanceTimeBy(15.seconds.inWholeMilliseconds)

        // New subscriber arrives before subscription timeout expires
        val job2 = launch {
            cache.get("key") {
                createCachedFlow()
            }.collect {}
        }
        runCurrent()

        // The entry was retained, so the provider was called again (to restart upstream)
        // but the entry itself was reused (not removed and recreated)
        assertEquals(1, createCount, "Provider should be not be created again")

        // Advance past the original subscription timeout - entry should NOT be removed
        advanceTimeBy(20.seconds.inWholeMilliseconds)
        runCurrent()

        // A third subscriber should still reuse the active entry
        val job3 = launch {
            cache.get("key") {
                createCachedFlow()
            }.collect {}
        }
        runCurrent()
        assertEquals(1, createCount, "Entry should still be cached since job2 is active")

        job2.cancel()
        job3.cancel()
    }

    /**
     * With both timeouts set to zero, the entry should be removed immediately
     * when all subscribers cancel (no retention).
     */
    @Test
    fun `zero timeouts remove entry immediately`() = runTest {
        val cache = FlowCache<String, Int>(
            backgroundScope,
            subscriptionTimeout = Duration.ZERO,
            replayTimeout = Duration.ZERO
        )
        var createCount = 0

        val job = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()

        job.cancel()
        runCurrent()

        // Immediately after cancel, entry should be gone
        val job2 = launch {
            cache.get("key") {
                createCount++
                flow { awaitCancellation() }
            }.collect {}
        }
        runCurrent()
        assertEquals(2, createCount, "Entry should have been removed immediately")

        job2.cancel()
    }
}
