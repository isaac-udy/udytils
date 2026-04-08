package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class RetryableSharedFlowTest {

    /**
     * The replay parameter must be at least 1, since RetryableSharedFlow relies on the replay
     * cache to detect errors and trigger retries.
     */
    @Test
    fun `asRetryableSharedFlow requires replay of at least 1`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            flowOf(1).asRetryableSharedFlow(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                replay = 0,
            )
        }
    }

    /**
     * The default replay value of 1 should be accepted without error.
     */
    @Test
    fun `asRetryableSharedFlow with default replay does not throw`() = runTest {
        flowOf(1).asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Eagerly,
        )
    }

    /**
     * A collector should receive values emitted by the upstream flow.
     */
    @Test
    fun `collector receives values from a successful upstream flow`() = runTest {
        val shared = flowOf("hello").asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Eagerly,
        )
        advanceUntilIdle()

        val result = shared.first()
        assertEquals("hello", result)
    }

    /**
     * When the upstream flow throws, the error should be propagated to the collector.
     * Using SharingStarted.Lazily so the first collector sees the error directly
     * (currentFailure is null when the first collector subscribes to an empty replay cache).
     */
    @Test
    fun `collector receives error when upstream flow throws`() = runTest {
        val testException = RuntimeException("upstream failure")
        val shared = flow<String> {
            throw testException
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Lazily,
        )

        val error = assertFailsWith<RuntimeException> {
            shared.first()
        }
        assertEquals("upstream failure", error.message)
    }

    /**
     * Core retry behavior: when a new collector subscribes after the upstream has failed,
     * it triggers a retry of the upstream flow. If the retry succeeds, the collector
     * receives the new value.
     */
    @Test
    fun `new collector after error triggers retry of upstream flow`() = runTest {
        var attempts = 0
        val shared = flow {
            attempts++
            if (attempts == 1) throw RuntimeException("fail")
            emit("success on attempt $attempts")
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Lazily,
        )

        // First collection triggers upstream, which fails
        assertFailsWith<RuntimeException> {
            shared.first()
        }
        advanceUntilIdle()
        assertEquals(1, attempts)

        // Second collection should trigger a retry of the upstream
        val result = shared.first()
        assertEquals("success on attempt 2", result)
        assertEquals(2, attempts)
    }

    /**
     * If the retry also fails (with a different exception instance), the new error
     * should be propagated to the collector.
     */
    @Test
    fun `retry after error propagates new error when retry also fails`() = runTest {
        var attempts = 0
        val shared = flow<String> {
            attempts++
            throw RuntimeException("fail attempt $attempts")
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Lazily,
        )

        // First collection fails
        val error1 = assertFailsWith<RuntimeException> {
            shared.first()
        }
        assertEquals("fail attempt 1", error1.message)
        advanceUntilIdle()

        // Second collection retries, but also fails with a new exception
        val error2 = assertFailsWith<RuntimeException> {
            shared.first()
        }
        assertEquals("fail attempt 2", error2.message)
        assertEquals(2, attempts)
    }

    /**
     * Multiple sequential retries should each trigger a new upstream attempt.
     */
    @Test
    fun `multiple sequential retries each trigger a new upstream attempt`() = runTest {
        var attempts = 0
        val shared = flow {
            attempts++
            if (attempts < 3) throw RuntimeException("fail attempt $attempts")
            emit("success on attempt $attempts")
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Lazily,
        )

        // First two collections fail
        assertFailsWith<RuntimeException> { shared.first() }
        advanceUntilIdle()

        assertFailsWith<RuntimeException> { shared.first() }
        advanceUntilIdle()

        // Third collection succeeds
        val result = shared.first()
        assertEquals("success on attempt 3", result)
        assertEquals(3, attempts)
    }

    /**
     * With replay=1 (the default), only the last emitted value should be available
     * to new collectors after the upstream completes.
     */
    @Test
    fun `replays last value to new collectors with default replay`() = runTest {
        val shared = flow {
            emit("first")
            emit("second")
            emit("third")
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Lazily,
        )

        // First collector consumes all values
        val firstCollectorValues = shared.take(3).toList()
        assertEquals(listOf("first", "second", "third"), firstCollectorValues)
        advanceUntilIdle()

        // New collector should get only the last replayed value (replay=1)
        val result = shared.first()
        assertEquals("third", result)
    }

    /**
     * With a larger replay value, new collectors should receive all replayed values.
     */
    @Test
    fun `replay parameter controls number of replayed values`() = runTest {
        val shared = flow {
            emit("first")
            emit("second")
            emit("third")
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Lazily,
            replay = 3,
        )

        // First collector consumes all values
        val firstCollectorValues = shared.take(3).toList()
        assertEquals(listOf("first", "second", "third"), firstCollectorValues)
        advanceUntilIdle()

        // New collector should get all 3 replayed values
        val values = mutableListOf<String>()
        val job = launch {
            shared.collect { values.add(it) }
        }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("first", "second", "third"), values)
    }

    /**
     * Multiple collectors should share the same upstream flow, not each trigger
     * a separate upstream execution.
     */
    @Test
    fun `multiple collectors share the same upstream`() = runTest {
        var startCount = 0
        val shared = flow {
            startCount++
            emit("value")
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Eagerly,
        )
        advanceUntilIdle()

        val result1 = shared.first()
        val result2 = shared.first()

        assertEquals("value", result1)
        assertEquals("value", result2)
        assertEquals(1, startCount)
    }

    /**
     * An active collector should receive all values emitted by a continuous upstream flow.
     */
    @Test
    fun `active collector receives all values from continuous upstream`() = runTest {
        val shared = flow {
            emit(1)
            emit(2)
            emit(3)
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Lazily,
        )

        val values = shared.take(3).toList()
        assertEquals(listOf(1, 2, 3), values)
    }

    /**
     * When the upstream emits a value and then throws, the last item in the replay cache
     * is the error. A new collector triggers a retry, and if the retry succeeds, the
     * collector receives the new value.
     */
    @Test
    fun `error after successful emission triggers retry for new collector`() = runTest {
        var attempts = 0
        val shared = flow {
            attempts++
            emit("value from attempt $attempts")
            if (attempts == 1) throw RuntimeException("fail after emit")
        }.asRetryableSharedFlow(
            scope = backgroundScope,
            started = SharingStarted.Lazily,
        )

        // First collector: receives the value, then gets the error
        val values = mutableListOf<String>()
        val error = assertFailsWith<RuntimeException> {
            shared.collect { values.add(it) }
        }
        assertEquals("fail after emit", error.message)
        assertEquals(listOf("value from attempt 1"), values)
        advanceUntilIdle()

        // The replay cache now contains the error. A new collector triggers retry.
        // The retry succeeds (attempt 2 doesn't throw), so the collector gets the new value.
        val result = shared.first()
        assertEquals("value from attempt 2", result)
        assertEquals(2, attempts)
    }
}
