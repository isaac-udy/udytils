package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * A throwable captured into `AsyncState.Error` by a state builder is only ever shown as UI state,
 * so `AsyncState.onErrorCaptured` is the one place an application can observe it — for the default
 * stack-trace print, or for crash reporting.
 */
class AsyncStateOnErrorCapturedTest {

    private val defaultHook = AsyncState.onErrorCaptured

    @AfterTest
    fun restoreDefaultHook() {
        AsyncState.onErrorCaptured = defaultHook
    }

    /**
     * `fromSuspending` captures a throwing block as `Error` rather than throwing to the collector;
     * the hook receives that captured throwable, once. It is the throwable the `Error` holds rather
     * than the instance the block threw, because coroutine stack-trace recovery copies an exception
     * as it crosses the flow boundary.
     */
    @Test
    fun `onErrorCaptured receives the throwable a fromSuspending block throws`() = runTest {
        val captured = mutableListOf<Throwable>()
        AsyncState.onErrorCaptured = { throwable -> captured += throwable }

        val results = AsyncState.fromSuspending<String> { throw RuntimeException("Block failed!") }
            .toList()

        val errorState = results[1]
        assertIs<AsyncState.Error<String>>(errorState, "Flow should emit Error when the block throws")
        assertEquals(1, captured.size, "The captured error should be reported exactly once")
        assertSame(errorState.error, captured.single(), "The hook should receive the captured error")
        assertEquals("Block failed!", captured.single().message)
    }
}
