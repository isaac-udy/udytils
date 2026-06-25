package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.asRetryableSharedFlow(
    scope: CoroutineScope,
    started: SharingStarted,
    replay: Int = 1,
): RetryableSharedFlow<T> {
    require(replay >= 1) {
        "RetryableSharedFlow must have a replay value of at least 1, but was $replay."
    }

    val shareTarget = this
    // The retry signal is a monotonically-increasing token, NOT the failure
    // itself. Every retry request is therefore a new value, so `flatMapLatest`
    // always restarts the upstream — even when the cached failure is VALUE-equal
    // to a previous one. The old design carried the Throwable through a
    // `distinctUntilChanged`, so two value-equal terminal errors (e.g. two
    // `UserAccessDeniedException`, which compare equal) collapsed into one
    // signal: a later subscriber's retry was silently dropped and the flow
    // latched forever, emitting neither a value nor an error.
    val retrySignal = MutableStateFlow(0L)

    return RetryableSharedFlow(
        retrySignal = retrySignal,
        flow = retrySignal
            .flatMapLatest {
                shareTarget
                    .map { value ->
                        Result.success(value)
                    }
                    .catch { throwable ->
                        emit(Result.failure(throwable))
                    }
            }
            .shareIn(
                scope = scope,
                started = started,
                replay = replay,
            )
    )
}

class RetryableSharedFlow<T> internal constructor(
    private val retrySignal: MutableStateFlow<Long>,
    private val flow: SharedFlow<Result<T>>
) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        val currentFailure = flow.replayCache.lastOrNull()?.exceptionOrNull()
        flow
            .onStart {
                if (currentFailure != null) {
                    // Bump the token to a fresh value so the upstream restarts
                    // regardless of whether this cached failure is value-equal to
                    // one a prior subscriber already retried on.
                    retrySignal.update { it + 1 }
                }
            }
            .dropWhile {
                currentFailure != null && it.exceptionOrNull() == currentFailure
            }
            .collect {
                collector.emit(it.getOrThrow())
            }
    }
}
