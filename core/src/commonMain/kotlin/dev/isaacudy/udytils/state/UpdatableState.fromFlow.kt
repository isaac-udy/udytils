package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

/**
 * Collects [flow] as a [Flow] of [UpdatableState], folding each emission into the previous state
 * so data survives loading and error phases.
 *
 * Starts as [UpdatableState.Empty] (or [UpdatableState.Data] when [initialData] is provided),
 * then applies [updateFrom] to every [AsyncState] produced by [AsyncState.fromFlow]: loading and
 * error emissions only update the operation state, successes replace the data. [onException]
 * follows the [AsyncState.fromFlow] semantics ([stopCollection], [retry], [silentRetry]).
 */
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

/**
 * Continues this state from [flow]: collects via [UpdatableState.Companion.fromFlow] with this
 * state's current data as the initial value, so existing data stays visible while the new
 * collection loads.
 */
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
