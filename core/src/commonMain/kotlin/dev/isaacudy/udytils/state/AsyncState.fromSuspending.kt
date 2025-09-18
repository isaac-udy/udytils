package dev.isaacudy.udytils.state

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.takeWhile
import kotlin.time.Duration.Companion.milliseconds

/**
 * FromSuspendingScope is used by AsyncState.fromSuspending to allow a suspending block to emit
 * into the AsyncState.Loading flow.
 *
 * Example:
 * ```kotlin
 * AsyncState
 *   .fromSuspending {
 *     emitProgress(0.0f)
 *     val firstResult = getFirstResult()
 *     emitProgress(0.33f)
 *     val secondResult = getSecondResult(firstResult)
 *     emitProgress(0.66f)
 *     return@fromSuspending getFinalResult(secondResult)
 *   }
 * ```
 */
class FromSuspendingScope<T>() {
    internal val progress = MutableStateFlow<Float?>(null)

    fun emitProgress(value: Float) {
        progress.value = value
    }

    fun emitIndeterminateProgress() {
        progress.value = null
    }
}

@OptIn(FlowPreview::class)
fun <T> AsyncState.Companion.fromSuspending(
    block: suspend FromSuspendingScope<T>.() -> T
): Flow<AsyncState<T>> {
    return flow {
        val scope = FromSuspendingScope<T>()

        val completionSignal = MutableSharedFlow<AsyncState<T>>()

        val output = merge(
            completionSignal,
            scope.progress
                .distinctUntilChangedBy {
                    when (it) {
                        null -> null
                        else -> (it * 100).toInt()
                    }
                }
                .debounce(32.milliseconds)
                .map { AsyncState.Loading(it) },
            flow {
                emit(AsyncState.Success(block(scope)))
            },
        ).takeWhile {
            !it.isIdle()
        }

        output.collect {
            emit(it)
            it.onSuccess {
                completionSignal.emit(AsyncState.Idle())
            }.onError {
                throw it
            }
        }
    }.catch {
        emit(AsyncState.Error(it))
    }
}
