package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

/**
 * Runs [block] once per collection (see [AsyncState.fromSuspending]) and folds its
 * Loading/Success/Error emissions into an [UpdatableState] via [updateFrom]. Starts as
 * [UpdatableState.Data] when [initialData] is provided, otherwise [UpdatableState.Empty], so
 * existing data remains available while the block runs or after it fails.
 */
fun <T: Any> UpdatableState.Companion.fromSuspending(
    initialData: T? = null,
    block: suspend FromSuspendingScope<T>.() -> T
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

/**
 * Re-runs a suspending update while preserving this state's current data: loading and error
 * phases keep [dataOrNull] visible, and a successful [block] replaces it.
 */
fun <T: Any> UpdatableState<T>.updateFromSuspending(
    block: suspend FromSuspendingScope<T>.() -> T
) : Flow<UpdatableState<T>> {
    return UpdatableState.fromSuspending(
        initialData = dataOrNull,
        block = block,
    )
}