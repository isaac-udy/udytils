package dev.isaacudy.udytils.state

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

fun <T> AsyncState.Companion.fromDeferred(
    deferred: Deferred<T>,
) : Flow<AsyncState<T>> {
    return AsyncState.Companion.fromSuspending { deferred.await() }
}