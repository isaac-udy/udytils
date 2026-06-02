package dev.isaacudy.udytils.coroutines

import dev.isaacudy.udytils.error.PresentableException
import dev.isaacudy.udytils.error.isRetryable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryTransientTest {

    private class Terminal : PresentableException(title = "Nope", message = "no", retryable = false)
    private class Transient : PresentableException(title = "Oops", message = "later", retryable = true)

    @Test
    fun `isRetryable reflects the retryable flag and the defaults`() {
        assertTrue(Transient().isRetryable())
        assertFalse(Terminal().isRetryable())
        assertTrue(RuntimeException("io").isRetryable())          // unknown -> treated as transient
        assertFalse(CancellationException("nope").isRetryable())  // cancellation never retries
    }

    @Test
    fun `retryTransient retries a retryable failure then succeeds`() = runTest {
        var attempts = 0
        val values = flow {
            attempts++
            if (attempts < 3) throw Transient()
            emit("ok")
        }.retryTransient().toList()
        assertEquals(listOf("ok"), values)
        assertEquals(3, attempts)
    }

    @Test
    fun `retryTransient does not retry a terminal failure`() = runTest {
        var attempts = 0
        assertFailsWith<Terminal> {
            flow<String> {
                attempts++
                throw Terminal()
            }.retryTransient().toList()
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `retryTransient treats unknown errors as transient`() = runTest {
        var attempts = 0
        val values = flow {
            attempts++
            if (attempts < 2) throw RuntimeException("blip")
            emit("recovered")
        }.retryTransient().toList()
        assertEquals(listOf("recovered"), values)
        assertEquals(2, attempts)
    }
}
