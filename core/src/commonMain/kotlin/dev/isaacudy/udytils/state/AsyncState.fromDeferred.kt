package dev.isaacudy.udytils.state

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

/**
 * Awaits [deferred] as a [Flow] of [AsyncState]: an indeterminate [AsyncState.Loading] while
 * awaiting, then [AsyncState.Success] with the result or [AsyncState.Error] if the deferred
 * failed. Equivalent to `AsyncState.fromSuspending { deferred.await() }`.
 */
fun <T> AsyncState.Companion.fromDeferred(
    deferred: Deferred<T>,
) : Flow<AsyncState<T>> {
    return AsyncState.Companion.fromSuspending { deferred.await() }
}