package dev.isaacudy.udytils.state

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.dropWhile
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

    /** Reports determinate progress in `0f..1f`, emitted downstream as [AsyncState.Loading]. */
    fun emitProgress(value: Float) {
        progress.value = value
    }

    /** Switches progress reporting back to indeterminate (a progress-less [AsyncState.Loading]). */
    fun emitIndeterminateProgress() {
        progress.value = null
    }
}

/**
 * Runs [block] once per collection and reports its lifecycle as a cold [Flow] of [AsyncState].
 *
 * The flow emits an indeterminate [AsyncState.Loading] immediately, an [AsyncState.Loading] with
 * progress whenever the block calls [FromSuspendingScope.emitProgress] (conflated to whole
 * percentage points and lightly debounced), and finally a single [AsyncState.Success] with the
 * block's return value. An exception thrown by the block is emitted as [AsyncState.Error] rather
 * than thrown to the collector; cancellation still propagates ([AsyncState.Error] refuses to
 * wrap a `CancellationException`).
 *
 * ```
 * AsyncState.fromSuspending {
 *     emitProgress(0f)
 *     val user = api.loadUser()
 *     emitProgress(0.5f)
 *     api.loadProfile(user)
 * }
 * ```
 */
@OptIn(FlowPreview::class)
fun <T> AsyncState.Companion.fromSuspending(
    block: suspend FromSuspendingScope<T>.() -> T
): Flow<AsyncState<T>> {
    return flow {
        emit(AsyncState.Loading())

        val scope = FromSuspendingScope<T>()

        val completionSignal = MutableSharedFlow<AsyncState<T>>()

        val output = merge(
            completionSignal,
            scope.progress
                // Drop leading indeterminate (null) progress — the initial indeterminate Loading
                // is already emitted above (`emit(AsyncState.Loading())`), so without this an
                // operation that never calls emitProgress would emit a redundant `Loading(null)`.
                // `dropWhile` (vs drop(1)) is conflation-safe: real progress values are never null,
                // so a determinate value is never dropped even if it races ahead of the initial.
                .dropWhile { it == null }
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
