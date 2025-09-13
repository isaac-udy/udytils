package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

fun <T: Any> UpdatableState.Companion.fromSuspending(
    initialData: T? = null,
    block: suspend () -> T
): Flow<UpdatableState<T>> {
    val initialState = when (initialData) {
        null -> UpdatableState.Empty(
            state = AsyncState.Idle()
        )

        else -> UpdatableState.Data(
            data = initialData,
            state = AsyncState.Idle()
        )
    }
    return AsyncState
        .fromSuspending(block)
        .scan(initialState) { currentState, asyncState ->
            currentState.updateFrom(asyncState)
        }
}

fun <T: Any> UpdatableState<T>.updateFromSuspending(
    block: suspend () -> T
) : Flow<UpdatableState<T>> {
    return UpdatableState.fromSuspending(
        initialData = dataOrNull,
        block = block,
    )
}