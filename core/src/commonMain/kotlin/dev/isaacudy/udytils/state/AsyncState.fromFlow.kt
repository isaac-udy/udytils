package dev.isaacudy.udytils.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun <T> AsyncState.Companion.fromFlow(
    flow: Flow<T>,
    onException: AsyncStateFlow.OnException = stopCollection(),
) : Flow<AsyncState<T>> {
    return flow
        .map<T, AsyncState<T>> {
            AsyncState.Success(it)
        }
        .onStart {
            emit(AsyncState.Loading())
        }
        .let {
            onException.handleErrors(it)
        }
}

object AsyncStateFlow {
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

fun stopCollection(): AsyncStateFlow.OnException {
    return AsyncStateFlow.OnException.StopCollection
}

fun retry(retryDelay: Duration = 5.seconds): AsyncStateFlow.OnException {
    return AsyncStateFlow.OnException.Retry(
        retryDelay = retryDelay,
        isSilent = false
    )
}
fun silentRetry(retryDelay: Duration = 5.seconds): AsyncStateFlow.OnException {
    return AsyncStateFlow.OnException.Retry(
        retryDelay = retryDelay,
        isSilent = false
    )
}

fun <T> Flow<T>.asAsyncState(
    onException: AsyncStateFlow.OnException = stopCollection(),
): Flow<AsyncState<T>> {
    return AsyncState.Companion.fromFlow(this, onException)
}
