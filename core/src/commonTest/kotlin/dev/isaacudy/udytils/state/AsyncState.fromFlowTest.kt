package dev.isaacudy.udytils.state

import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.asAsyncState
import dev.isaacudy.udytils.state.fromFlow
import dev.isaacudy.udytils.state.getOrNull
import dev.isaacudy.udytils.state.isLoading
import dev.isaacudy.udytils.state.retry
import dev.isaacudy.udytils.state.silentRetry
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * These functions are designed to convert any `Flow` into a `Flow<AsyncState<T>>`,
 * which is useful for representing the state of asynchronous data streams in a UI.
 * The tests cover the default behavior, as well as the different strategies for handling exceptions.
 */
class AsyncStateFromFlowTest {

    /**
     * Default success scenario for a flow that completes without errors.
     * The flow will emit: `Loading` -> `Success` (for each item)
     */
    @Test
    fun `fromFlow should emit Loading then Success for a successful single-item flow`() = runTest {
        // Create a simple flow that emits one value and completes.
        val sourceFlow = flow { emit("Success Data") }

        // Usage: Wrap the source flow with `AsyncState.fromFlow`.
        val asyncStateFlow = AsyncState.fromFlow(sourceFlow)
        val results = asyncStateFlow.toList()

        assertIs<AsyncState.Loading<String>>(results[0], "Flow should start with Loading")

        val successState = results[1]
        assertIs<AsyncState.Success<String>>(successState, "Flow should emit Success with the data")
        assertEquals("Success Data", successState.data)
        assertEquals(2, results.size, "The flow completes")
    }

    /**
     * Default exception handling strategy (`stopCollection`).
     * If the source flow throws an exception, it is caught and the flow emits:
     * `Loading` -> `Error`. The collection then stops.
     */
    @Test
    fun `fromFlow with default 'stopCollection' should emit Error and stop on exception`() =
        runTest {
            val testException = IllegalStateException("Something went wrong!")

            // Create a flow that emits a value, then throws an error.
            val asyncStateFlow = flow {
                emit("First value")
                throw testException
            }.asAsyncState() // Default `onException` parameter is `stopCollection()`

            val results = asyncStateFlow.toList()

            assertIs<AsyncState.Loading<String>>(results[0], "Flow should start with Loading")

            // Emits Success for the first item.
            assertIs<AsyncState.Success<String>>(results[1])
            assertEquals("First value", results[1].getOrNull())

            // Catches the exception and emits an Error state.
            val errorState = results[2]
            assertIs<AsyncState.Error<String>>(errorState, "Flow should emit Error on exception")
            assertEquals(
                testException,
                errorState.error,
                "Error state should contain the original exception"
            )

            assertEquals(3, results.size, "Flow should not continue after the error")
        }

    /**
     * This is the `retry` exception handling strategy.
     * When an error occurs, this strategy emits an `Error` state, waits for the specified delay,
     * emits a new `Loading` state, and then re-subscribes to the original flow.
     */
    @Test
    fun `fromFlow with 'retry' should emit Error, Loading, and then retry the flow`() = runTest {
        val testException = RuntimeException("Temporary failure")
        var attempt = 0

        val asyncStateFlow = flow {
            attempt++
            if (attempt == 1) {
                throw testException // Fail on the first attempt
            }
            emit("Success on attempt $attempt") // Succeed on the second
        }.asAsyncState(onException = retry(retryDelay = 50.milliseconds))

        val results = asyncStateFlow.toList()

        assertEquals(4, results.size)

        assertIs<AsyncState.Loading<String>>(results[0], "Should have initial loading state")

        val errorState = results[1]
        assertIs<AsyncState.Error<String>>(errorState, "Should emit Error on first attempt")
        assertEquals(testException, errorState.error)

        assertIs<AsyncState.Loading<String>>(results[2], "Should emit Loading before retry")

        val successState = results[3]
        assertIs<AsyncState.Success<String>>(
            successState,
            "Should emit Success on the second attempt"
        )
        assertEquals("Success on attempt 2", successState.data)
    }

    /**
     * This is the `silentRetry` exception handling strategy.
     * This is similar to `retry`, but it does *not* emit a new `Loading` state after the delay.
     * It emits `Error`, waits, and then the flow is re-subscribed. This is useful for background retries
     * where you don't want to flash a loading indicator in the UI.
     * Note: The current implementation of silentRetry in the file is incorrect and behaves like a normal retry.
     * This test is written against the intended behavior of a silent retry.
     */
    @Test
    fun `fromFlow with 'silentRetry' should emit Error and retry without a new Loading state`() =
        runTest {
            val testException = RuntimeException("Temporary failure")
            var attempt = 0

            val asyncStateFlow = flow {
                attempt++
                if (attempt == 1) throw testException
                emit("Success on attempt $attempt")
            }.asAsyncState(onException = silentRetry(50.milliseconds))

            val results = asyncStateFlow.toList()

            // Verification:
            // 1. Initial Loading state.
            assertIs<AsyncState.Loading<String>>(results[0])

            // 2. First attempt fails, emitting an Error state.
            val errorState = results[1]
            assertIs<AsyncState.Error<String>>(errorState)
            assertEquals(testException, errorState.error)

            // 3. Second attempt succeeds, emitting a Success state directly after the error.
            val successState = results[2]
            assertIs<AsyncState.Success<String>>(successState)
            assertEquals("Success on attempt 2", successState.data)

            // 4. There should be NO intermediate Loading state.
            val hasIntermediateLoading =
                results.slice(1 until results.lastIndex).any { it.isLoading() }
            assertTrue(
                !hasIntermediateLoading,
                "A silent retry should not emit an intermediate Loading state"
            )

            assertEquals(3, results.size)
        }
}
