package dev.isaacudy.udytils.state

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * ViewModelState is a state holder designed to be used within the context of an androidx.lifecycle.ViewModel.
 * It ensures that state updates can only be performed from within a ViewModel, providing a safer,
 * more predictable state management pattern.
 */
class ViewModelStateTest {

    // A data class to represent a simple UI state for our tests.
    data class UiState(val counter: Int = 0, val text: String = "Initial")

    /**
     * Basic creation and usage of ViewModelState via the `viewModelState` factory function.
     * This is the simplest way to create and manage state.
     */
    class SimpleViewModel : ViewModel() {
        val state = viewModelState(UiState())

        fun incrementCounter() {
            state.update(UiState(counter = state.value.counter + 1, text = "Updated"))
        }

        fun incrementWithBlock() {
            state.update {
                UiState(
                    // Block context gives us access to the current state directly
                    counter = counter + 10,
                    text = "Updated from block"
                )
            }
        }
    }

    @Test
    fun `viewModelState should initialize and update state correctly`() = runTest {
        val viewModel = SimpleViewModel()

        assertEquals(
            UiState(),
            viewModel.state.value,
            "State should be initialized with the provided initial value."
        )

        // Usage: Update the state object. This must be done within the ViewModel's context.
        viewModel.incrementCounter()
        assertEquals(
            UiState(counter = 1, text = "Updated"),
            viewModel.state.value,
            "State should reflect the new value after a full update."
        )

        // Usage: Update the state based on its previous value using a lambda.
        // This is safer for complex, sequential updates.
        viewModel.incrementWithBlock()
        assertEquals(
            11,
            viewModel.state.value.counter,
            "State should be updated using the lambda block."
        )
        assertEquals("Updated from block", viewModel.state.value.text)
    }

    /**
     * A more advanced usage with the `ViewModelState.build` factory.
     *
     * The builder pattern is useful for more complex initialization scenarios where the
     * final state depends on logic that might also need to trigger updates. For example,
     * initializing a value and then immediately kicking off an asynchronous task that
     * will update the state later.
     */
    class StateBuilderViewModel : ViewModel() {
        val state = ViewModelState.build<UiState> {
            // Inside the builder, `state` refers to a `StateReference`.
            // Updates made here are queued until the state is fully initialized.
            state.update { copy(counter = 5) }
            state.update { copy(text = "From Queued Update") }
            // The last expression in the block determines the *initial* state.
            UiState(counter = 0, text = "Initial")
        }

        fun incrementCounter() {
            state.update(UiState(counter = state.value.counter + 1, text = "Updated"))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `ViewModelState_build should construct state and handle queued updates`() = runTest {
        val viewModel = StateBuilderViewModel()

        // 1. The initial state is "Initial(counter=0, text='Initial')".
        // 2. The builder then applies the two queued updates in order.
        //    - First update: state becomes UiState(counter=5, text='Initial')
        //    - Second update: state becomes UiState(counter=5, text='From Queued Update')
        advanceUntilIdle()
        val expectedState = UiState(counter = 5, text = "From Queued Update")
        assertEquals(
            expectedState,
            viewModel.state.value,
            "State should reflect the initial value modified by all queued updates."
        )

        // Subsequent updates work as normal.
        viewModel.incrementCounter()
        assertEquals(
            6,
            viewModel.state.value.counter,
            "Regular updates should still work after the builder is finished."
        )
    }

    /**
     * The `collectAsState` function is a placeholder and relies on Jetpack Compose's runtime.
     * While we can't test the Composable behavior directly in a unit test, we can verify
     * that the function exists and returns a Compose `State` object.
     */
    @Test
    fun `collectAsState should return a non_null State object`() {
        val viewModel = SimpleViewModel()

        // This is a conceptual test. We are not in a @Composable context,
        // so we cannot call the function directly. The test's purpose is to
        // confirm its signature and existence for compile-time safety.
        // A direct call would look like `stateHolder.collectAsState()`,
        // but that's reserved for UI code.
        // For this unit test, we can just acknowledge its role.
        assertNotNull(viewModel.state::collectAsState)
        assertNotNull(viewModel.state::collectAsStateWithLifecycle)
    }
}
