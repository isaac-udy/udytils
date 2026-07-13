package dev.isaacudy.udytils.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Wraps [flow] so that every emission becomes an [AsyncState.Success], prefixed with a single
 * indeterminate [AsyncState.Loading] when collection starts.
 *
 * [onException] decides what happens when the upstream flow throws: [stopCollection] (the
 * default) emits [AsyncState.Error] and ends the flow, while [retry] and [silentRetry] emit the
 * error and then re-collect the upstream after a delay. See also [Flow.asAsyncState] for the
 * fluent form.
 */
fun <T> AsyncState.Companion.fromFlow(
    flow: Flow<T>,
    onException: AsyncStateFlow.OnException = stopCollection(),
): Flow<AsyncState<T>> {
    return flow
        .map<T, AsyncState<T>> {
            AsyncState.Success(it)
        }
        .let {
            onException.handleErrors(it)
        }
        .onStart {
            emit(AsyncState.Loading())
        }
}

/** Namespace for the error-handling strategies used by [AsyncState.fromFlow] / [Flow.asAsyncState]. */
object AsyncStateFlow {
    /**
     * Strategy applied when the upstream flow throws. Use the factory functions
     * [stopCollection], [retry] and [silentRetry] rather than referencing implementations
     * directly.
     */
    sealed interface OnException {
        fun <T> handleErrors(flow: Flow<AsyncState<T>>): Flow<AsyncState<T>>

        object StopCollection : OnException {
            override fun <T> handleErrors(flow: Flow<AsyncState<T>>): Flow<AsyncState<T>> {
                return flow
                    .catch { e ->
                        emit(AsyncState.Error(e))
                    }
            }
        }

        class Retry(
            private val retryDelay: Duration,
            private val isSilent: Boolean
        ) : OnException {
            override fun <T> handleErrors(flow: Flow<AsyncState<T>>): Flow<AsyncState<T>> {
                return flow
                    .retryWhen { cause, _ ->
                        if (cause is CancellationException) return@retryWhen false
                        emit(AsyncState.Error(cause))
                        delay(retryDelay)
                        if (!isSilent) emit(AsyncState.Loading())
                        true
                    }
            }

        }
    }
}

/**
 * [AsyncState.fromFlow] strategy: when the upstream throws, emit [AsyncState.Error] and stop
 * collecting. This is the default.
 */
fun stopCollection(): AsyncStateFlow.OnException {
    return AsyncStateFlow.OnException.StopCollection
}

/**
 * [AsyncState.fromFlow] strategy: when the upstream throws, emit [AsyncState.Error], wait
 * [retryDelay], emit [AsyncState.Loading], and re-collect the upstream. `CancellationException`
 * is never retried.
 */
fun retry(retryDelay: Duration = 5.seconds): AsyncStateFlow.OnException {
    return AsyncStateFlow.OnException.Retry(
        retryDelay = retryDelay,
        isSilent = false
    )
}

/**
 * Like [retry], but doesn't emit [AsyncState.Loading] between attempts — the UI keeps showing
 * the emitted [AsyncState.Error] (or the last data) instead of flashing back to a loading state.
 */
fun silentRetry(retryDelay: Duration = 5.seconds): AsyncStateFlow.OnException {
    return AsyncStateFlow.OnException.Retry(
        retryDelay = retryDelay,
        isSilent = true
    )
}

/**
 * Fluent alias for [AsyncState.fromFlow]: prefixes a [AsyncState.Loading], wraps each emission
 * in [AsyncState.Success], and handles upstream exceptions according to [onException].
 *
 * ```
 * repository.observeUsers()
 *     .asAsyncState(onException = retry())
 *     .collect { state -> /* ... */ }
 * ```
 */
fun <T> Flow<T>.asAsyncState(
    onException: AsyncStateFlow.OnException = stopCollection(),
): Flow<AsyncState<T>> {
    return AsyncState.fromFlow(this, onException)
}
