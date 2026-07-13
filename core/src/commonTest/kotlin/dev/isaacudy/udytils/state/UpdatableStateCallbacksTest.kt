package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatableStateCallbacksTest {

    @Test
    fun onEmpty_runs_only_for_empty() {
        var calledForEmpty = false
        var calledForData = false
        UpdatableState.Empty<String>().onEmpty { calledForEmpty = true }
        UpdatableState.Data("value").onEmpty { calledForData = true }
        assertTrue(calledForEmpty, "onEmpty must fire for Empty")
        assertFalse(calledForData, "onEmpty must not fire for Data")
    }

    @Test
    fun onData_runs_only_for_data() {
        val seen = mutableListOf<String>()
        UpdatableState.Data("value").onData { seen += it }
        UpdatableState.Empty<String>().onData { seen += it }
        assertEquals(listOf("value"), seen, "onData must fire for Data and not for Empty")
    }

    @Test
    fun onEachEmpty_fires_per_empty_emission_and_passes_everything_through() = runTest {
        var emptyCalls = 0
        val upstream = listOf(
            UpdatableState.Empty<String>(),
            UpdatableState.Data("a"),
            UpdatableState.Empty(state = AsyncState.Loading()),
        )
        val emitted = flowOf(*upstream.toTypedArray())
            .onEachEmpty { emptyCalls++ }
            .toList()
        assertEquals(2, emptyCalls, "one callback per Empty emission")
        assertEquals(upstream, emitted, "all emissions pass through unchanged")
    }
}
