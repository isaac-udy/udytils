package dev.isaacudy.udytils.state

import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.fromDeferred
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `fromDeferred` is a convenient wrapper for converting a `Deferred<T>`—a value that will be
 * available in the future—into a `Flow<AsyncState<T>>`. It simplifies handling the lifecycle
 * of an asynchronous operation, from loading to completion or failure.
 *
 * Since it's built on `fromSuspending`, it follows the same emission pattern:
 * 1. `Loading` (as soon as the flow is collected)
 * 2. `Success` (when the Deferred completes successfully)
 * or
 * 1. `Loading`
 * 2. `Error` (if the Deferred is cancelled or fails)
 */
class AsyncStateFromDeferredTest {

    /**
     * Documents the primary success scenario.
     * The `Deferred` completes successfully, and the flow emits the standard sequence:
     * `Loading` -> `Success` -> `Idle`.
     */
    @Test
    fun `fromDeferred should emit Loading, Success, and Idle for a successful deferred`() =
        runTest {
            // Setup: Create a Deferred that will complete with a value after a delay.
            val deferred = async {
                delay(50) // Simulate network latency or computation
                "Deferred Result"
            }

            // Usage: Pass the Deferred object to the `fromDeferred` function.
            val flow = AsyncState.fromDeferred(deferred)

            // Collect all emissions from the resulting flow.
            val results = flow.toList()

            // Verification: Check the sequence of emitted states.
            // 1. It immediately emits a Loading state upon collection.
            assertIs<AsyncState.Loading<String>>(results[0], "Flow should start with Loading")

            // 2. After the deferred completes, it emits a Success state with the value.
            val successState = results[1]
            assertIs<AsyncState.Success<String>>(
                successState,
                "Flow should emit Success upon completion"
            )
            assertEquals("Deferred Result", successState.data)
            assertEquals(2, results.size)
        }

    /**
     * Documents the failure scenario.
     * If the `Deferred` is cancelled or fails with an exception, the flow emits `Error`.
     */
    @Test
    fun `fromDeferred should emit Loading then Error if the deferred fails`() = runTest {
        val failedDeferred = CompletableDeferred<String>()
        failedDeferred.completeExceptionally(RuntimeException("Calculation failed!"))

        val flow = AsyncState.fromDeferred(failedDeferred)
        val results = flow.toList()

        val errorState = results[0]
        assertIs<AsyncState.Error<String>>(errorState, "Flow should emit Error when deferred fails")
        assertIs<RuntimeException>(
            errorState.error,
            "The error state should contain the original exception"
        )
        assertEquals("Calculation failed!", errorState.error.message)
    }
}
