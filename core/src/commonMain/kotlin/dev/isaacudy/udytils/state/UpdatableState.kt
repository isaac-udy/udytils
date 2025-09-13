package dev.isaacudy.udytils.state

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

sealed interface UpdatableState<T: Any> {
    val state: AsyncState<Unit>

    @Immutable
    data class Empty<T: Any>(
        override val state: AsyncState<Unit> = AsyncState.Idle()
    ) : UpdatableState<T>

    @Immutable
    data class Data<T: Any>(
        val data: T,
        override val state: AsyncState<Unit> = AsyncState.Idle()
    ) : UpdatableState<T>

    companion object
}

val <T: Any> UpdatableState<T>.dataOrNull: T?
    get() = when (this) {
        is UpdatableState.Data<T> -> data
        is UpdatableState.Empty<T> -> null
    }

fun <T: Any> UpdatableState<T>.updateFrom(
    state: AsyncState<T>
): UpdatableState<T> {
    return when (this) {
        is UpdatableState.Empty<T> -> when (state) {
            is AsyncState.Idle<T> -> copy(state = state.asUnit())
            is AsyncState.Loading<T> -> copy(state = state.asUnit())
            is AsyncState.Error<*> -> copy(state = state.asUnit())
            is AsyncState.Success<T> -> UpdatableState.Data(
                data = state.data,
                state = state.asUnit()
            )
        }

        is UpdatableState.Data<T> -> when (state) {
            is AsyncState.Idle<T> -> copy(state = state.asUnit())
            is AsyncState.Loading<T> -> copy(state = state.asUnit())
            is AsyncState.Error<*> -> copy(state = state.asUnit())
            is AsyncState.Success<T> -> UpdatableState.Data(
                data = state.data,
                state = state.asUnit()
            )
        }
    }
}

inline fun <T: Any, R: Any> UpdatableState<T>.map(block: (T) -> R): UpdatableState<R> {
    return when (this) {
        is UpdatableState.Empty<T> -> {
            @Suppress("UNCHECKED_CAST")
            this as UpdatableState.Empty<R>
        }
        is UpdatableState.Data<T> -> UpdatableState.Data(
            data = block(data),
            state = state,
        )
    }
}

inline fun <T: Any, R: Any> UpdatableState.Data<T>.map(block: (T) -> R): UpdatableState.Data<R> {
    return UpdatableState.Data(
        data = block(data),
        state = state,
    )
}

fun <T: Any>  UpdatableState<T>.orDefaultData(data: T): UpdatableState.Data<T> {
    return when (this) {
        is UpdatableState.Empty -> UpdatableState.Data(
            data = data,
            state = state,
        )
        is UpdatableState.Data -> this
    }
}

inline fun <T: Any> UpdatableState<T>.onEmpty(block: UpdatableState<T>.() -> Unit): UpdatableState<T> {
    if (this is UpdatableState.Data) {
        block()
    }
    return this
}

inline fun <T: Any> UpdatableState<T>.onData(block: UpdatableState<T>.(T) -> Unit): UpdatableState<T> {
    if (this is UpdatableState.Data) {
        block(data)
    }
    return this
}

fun <T: Any> Flow<UpdatableState<T>>.onEachEmpty(block: suspend () -> Unit): Flow<UpdatableState<T>> {
    return onEach { state ->
        state.onEmpty { block() }
    }
}

fun <T: Any> Flow<UpdatableState<T>>.onEachData(block: suspend (T) -> Unit): Flow<UpdatableState<T>> {
    return onEach { state ->
        state.onData { block(it) }
    }
}

fun <T: Any, R: Any> Flow<UpdatableState<T>>.mapLatestData(block: suspend (T) -> R): Flow<UpdatableState<R>> {
    return map { updatableState ->
        updatableState.map { data -> block(data) }
    }
}
