package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun <T> AsyncState.Companion.fromSuspending(block: suspend () -> T): Flow<AsyncState<T>> {
    return flow { emit(block()) }
        .asAsyncState(stopCollection())
}
