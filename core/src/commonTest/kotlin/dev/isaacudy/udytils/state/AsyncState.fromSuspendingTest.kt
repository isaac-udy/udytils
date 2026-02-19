package dev.isaacudy.udytils.state

import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.fromSuspending
import dev.isaacudy.udytils.state.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `fromSuspending` is a powerful tool for wrapping a suspending operation (like a network call
 * or database query) into a `Flow<AsyncState<T>>`. It elegantly handles loading states,
 * progress updates, success, and errors.
 */
class AsyncStateFromSuspendingTest {

    @Test
    fun `fromSuspending should emit Loading, then Success on a successful operation`() =
        runTest {
            val flow = AsyncState.fromSuspending {
                // Simulate some background work
                delay(50)
                "Operation Successful"
            }

            // The toList() function collects all emitted values from the flow.
            val results = flow.toList()

            val loading = results[0]
            assertIs<AsyncState.Loading<String>>(loading, "Flow should start with a Loading state")
            assertNull(loading.progress, "Initial loading state should be indeterminate")

            val success = results[1]
            assertIs<AsyncState.Success<String>>(
                success,
                "Flow should emit Success with the result"
            )
            assertEquals("Operation Successful", success.data)
        }

    @Test
    fun `fromSuspending should emit Error when the operation throws an exception`() = runTest {
        // The exception is thrown from inside the block.
        val flow = AsyncState.fromSuspending<String> {
            delay(50)
            throw RuntimeException("Network call failed!")
        }

        val results = flow.toList()

        assertIs<AsyncState.Loading<String>>(results[0], "Flow should still start with Loading")

        val errorState = results[1]
        assertIs<AsyncState.Error<String>>(errorState, "Flow should then emit Error on exception")
        assertIs<RuntimeException>(
            errorState.error,
            "The error state should contain the thrown exception"
        )
        assertEquals("Network call failed!", errorState.error.message)
    }

    /**
     * Documents the usage of the `FromSuspendingScope` to report progress.
     * The `emitProgress()` function allows you to provide fine-grained, determinate progress
     * updates from within your suspending operation.
     */
    @Test
    fun `fromSuspending should emit determinate progress updates when requested`() = runTest {
        val flow = AsyncState.fromSuspending {
            // Usage: Call `emitProgress` to update the loading state.
            emitProgress(0.0f) // Initial progress
            delay(40)
            emitProgress(0.5f) // Halfway progress
            delay(40)
            emitProgress(1.0f) // Final progress
            delay(40)
            "Progress Reported"
        }

        val results = flow.toList()

        val loadingStates = results.filterIsInstance<AsyncState.Loading<String>>()
        val success = results.last()

        // It should emit loading states with the specified progress.
        // Note: Debouncing might merge rapid emissions, so we check for the key values.
        assertTrue(loadingStates.any { it.progress == 0.0f }, "Should emit 0% progress")
        assertTrue(loadingStates.any { it.progress == 0.5f }, "Should emit 50% progress")
        assertTrue(loadingStates.any { it.progress == 1.0f }, "Should emit 100% progress")

        assertIs<AsyncState.Success<String>>(success, "It finishes with Success as usual")
        assertEquals("Progress Reported", success.data)
    }

    /**
     * We can switch between indeterminate and determinate progress.
     * This is useful for multi-stage operations where some parts have trackable progress
     * and others do not.
     */
    @Test
    fun `fromSuspending should handle switching between indeterminate and determinate progress`() =
        runTest {
            val flow = AsyncState.fromSuspending {
                // Stage 1: Determinate progress
                emitProgress(0.25f)
                delay(40)

                // Stage 2: An operation with unknown duration
                emitIndeterminateProgress()
                delay(50)

                // Stage 3: Back to determinate progress
                emitProgress(0.75f)
                delay(40)

                "Mixed Progress"
            }

            val results = flow.toList()

            val loadingStates = results.filterIsInstance<AsyncState.Loading<String>>()

            assertTrue(loadingStates.any { it.progress == 0.25f }, "Should emit 25% progress")
            assertTrue(
                loadingStates.any { it.progress == null },
                "Should emit an indeterminate progress state"
            )
            assertTrue(loadingStates.any { it.progress == 0.75f }, "Should emit 75% progress")
            assertTrue(results.last().isSuccess(), "Flow should end with a Success state")
        }
}
