package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

fun <T : Any> UpdatableState.Companion.fromFlow(
    flow: Flow<T>,
    initialData: T? = null,
    onException: AsyncStateFlow.OnException = stopCollection(),
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
        .fromFlow(flow, onException)
        .scan(initialState) { currentState, asyncState ->
            currentState.updateFrom(asyncState)
        }
}

fun <T: Any> UpdatableState<T>.updateFromFlow(
    flow: Flow<T>,
    onException: AsyncStateFlow.OnException = stopCollection(),
) : Flow<UpdatableState<T>> {
    return UpdatableState.fromFlow(
        flow = flow,
        initialData = dataOrNull,
        onException = onException
    )
}
