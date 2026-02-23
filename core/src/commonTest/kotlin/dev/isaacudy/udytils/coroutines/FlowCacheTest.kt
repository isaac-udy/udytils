package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class FlowCacheTest {

    /**
     * Basic usage: get returns values emitted by the upstream cold flow.
     */
    @Test
    fun `get returns values from the flow`() = runTest {
        val cache = FlowCache<String, Int>(backgroundScope)

        val result = cache.get("key") { flowOf(42) }.first()
        assertEquals(42, result)
    }

    /**
     * The provider block should only be called once for a given key when there are
     * active subscribers keeping the cache entry alive.
     */
    @Test
    fun `get reuses cached flow for the same key`() = runTest {
        val cache = FlowCache<String, Int>(backgroundScope)
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
        val cache = FlowCache<String, Int>(backgroundScope)

        val result1 = cache.get("key1") { flowOf(1) }.first()
        val result2 = cache.get("key2") { flowOf(2) }.first()

        assertEquals(1, result1)
        assertEquals(2, result2)
    }

    /**
     * When the last subscriber cancels, the cache entry should be removed.
     * A subsequent get should call the provider again to create a new flow.
     */
    @Test
    fun `cache entry is removed when all subscribers cancel`() = runTest {
        val cache = FlowCache<String, Int>(backgroundScope)
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
        val cache = FlowCache<String, Int>(backgroundScope)
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

    /**
     * When a non-cancellation error occurs in the upstream flow, the cache entry should
     * be immediately removed. The error is propagated to subscribers.
     */
    @Test
    fun `cache entry is removed on upstream error`() = runTest {
        val cache = FlowCache<String, Int>(backgroundScope)
        var createCount = 0

        val error = assertFailsWith<RuntimeException> {
            cache.get("key") {
                createCount++
                flow {
                    emit(1)
                    throw RuntimeException("test error")
                }
            }.collect {}
        }
        assertEquals("test error", error.message)
        assertEquals(1, createCount)
        runCurrent()

        // Cache should be cleared; a new get should invoke the provider again
        val result = cache.get("key") { createCount++; flowOf(99) }.first()
        assertEquals(99, result)
        assertEquals(2, createCount)
    }

    /**
     * After an error removes a cache entry, a new get with the same key should
     * create a completely fresh flow via the provider.
     */
    @Test
    fun `fresh entry is created after error eviction`() = runTest {
        val cache = FlowCache<String, Int>(backgroundScope)
        var attempts = 0

        // First get: error flow
        assertFailsWith<RuntimeException> {
            cache.get("key") {
                attempts++
                flow<Int> { throw RuntimeException("error") }
            }.first()
        }
        assertEquals(1, attempts)

        // Second get: should create a new entry with the success flow
        val result = cache.get("key") { attempts++; flowOf(42) }.first()
        assertEquals(42, result)
        assertEquals(2, attempts)
    }

    /**
     * Multiple active subscribers should all receive values from the same underlying
     * upstream flow, demonstrating that the cold flow is shared.
     */
    @Test
    fun `multiple subscribers receive values from the same shared upstream`() = runTest {
        val cache = FlowCache<String, Int>(backgroundScope)
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
     * This verifies that the FlowCache actually shares the flow.
     */
    @Test
    fun `upstream flow is only collected once for multiple subscribers`() = runTest {
        val cache = FlowCache<String, Int>(backgroundScope)
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
}
