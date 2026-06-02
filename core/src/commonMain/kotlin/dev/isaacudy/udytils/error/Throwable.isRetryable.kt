package dev.isaacudy.udytils.error

import kotlin.coroutines.cancellation.CancellationException

/**
 * Whether retrying the operation that produced this error could plausibly succeed.
 *
 * The signal is [ErrorMessage.retryable], which the producer (e.g. a server error mapper or a
 * domain exception) sets per error: transient / unknown failures are retryable, terminal domain
 * errors (access-denied, validation, not-found) are not. This is the single flag that drives both
 * streaming auto-retry (retry while retryable) and unary error UI (offer a "Retry" action while
 * retryable).
 *
 * - A [CancellationException] is never retryable (cancellation must always propagate).
 * - A [PresentableException] uses its [ErrorMessage.retryable].
 * - Anything else (a raw transport / IO error with no attached message) is treated as transient,
 *   since those are usually connection blips worth retrying.
 */
fun Throwable.isRetryable(): Boolean = when (this) {
    is CancellationException -> false
    is PresentableException -> errorMessage.retryable
    else -> true
}
