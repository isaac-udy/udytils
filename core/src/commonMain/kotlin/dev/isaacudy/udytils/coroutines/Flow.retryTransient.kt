package dev.isaacudy.udytils.coroutines

import dev.isaacudy.udytils.error.isRetryable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Retries this flow with exponential backoff while the failure [isRetryable] (transient or
 * unknown), and lets a terminal failure (one the producer marked non-retryable — access-denied,
 * validation, etc.) propagate to the collector.
 *
 * This is the streaming counterpart of the `retryable` flag: a server error mapper / domain
 * exception decides what is worth retrying, and this operator obeys it — so an "observe latest
 * state" flow rides out transient drops without surfacing anything, but a genuinely terminal
 * error still reaches the consumer (where it becomes e.g. an `AsyncState.Error`). Cancellation is
 * never retried.
 *
 * Apply it on the source flow (e.g. before `asRetryableSharedFlow`) so the shared/observed stream
 * is resilient at the point closest to the transport.
 */
fun <T> Flow<T>.retryTransient(
    initialDelay: Duration = 1.seconds,
    maxDelay: Duration = 30.seconds,
): Flow<T> = retryWhen { cause, attempt ->
    if (!cause.isRetryable()) return@retryWhen false
    // Exponential backoff, capped. Clamp the exponent so the Double pow can't overflow before the
    // duration is coerced down to maxDelay.
    val factor = 2.0.pow(attempt.coerceAtMost(16).toInt())
    delay((initialDelay * factor).coerceAtMost(maxDelay))
    true
}
