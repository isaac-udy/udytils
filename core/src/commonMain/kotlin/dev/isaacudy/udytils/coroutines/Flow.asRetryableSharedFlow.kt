package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

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
    val retrySignal = MutableSharedFlow<Throwable?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).also { it.tryEmit(null) }

    return RetryableSharedFlow(
        retrySignal = retrySignal,
        flow = retrySignal
            .distinctUntilChanged()
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
    private val retrySignal: MutableSharedFlow<Throwable?>,
    private val flow: SharedFlow<Result<T>>
) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        val currentFailure = flow.replayCache.lastOrNull()?.exceptionOrNull()
        flow
            .onStart {
                if (currentFailure != null) {
                    retrySignal.tryEmit(currentFailure)
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