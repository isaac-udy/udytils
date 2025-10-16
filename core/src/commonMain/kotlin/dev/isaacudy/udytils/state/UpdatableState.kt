package dev.isaacudy.udytils.state

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface UpdatableState<T : Any> {
    val state: AsyncState<Unit>

    @Immutable
    data class Empty<T : Any>(
        override val state: AsyncState<Unit> = AsyncState.Idle()
    ) : UpdatableState<T>

    @Immutable
    data class Data<T : Any>(
        val data: T,
        override val state: AsyncState<Unit> = AsyncState.Idle()
    ) : UpdatableState<T>

    companion object
}

@OptIn(ExperimentalContracts::class)
fun <T : Any> UpdatableState<T>.isEmpty() : Boolean {
    contract {
        returns(true) implies (this@isEmpty is UpdatableState.Empty<T>)
    }
    return when (this) {
        is UpdatableState.Empty -> true
        is UpdatableState.Data -> false
    }
}

val <T : Any> UpdatableState<T>.dataOrNull: T?
    get() = when (this) {
        is UpdatableState.Data<T> -> data
        is UpdatableState.Empty<T> -> null
    }

val <T : Any> UpdatableState<List<T>>.dataOrEmpty: List<T>
    get() = when (this) {
        is UpdatableState.Data<List<T>> -> data
        is UpdatableState.Empty<List<T>> -> emptyList()
    }

val <K, V> UpdatableState<Map<K, V>>.dataOrEmpty: Map<K, V>
    get() = when (this) {
        is UpdatableState.Data<Map<K, V>> -> data
        is UpdatableState.Empty<Map<K, V>> -> emptyMap()
    }


fun <T : Any> UpdatableState<T>.updateFrom(
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

fun <T : Any> UpdatableState.Data<T>.updateFrom(
    state: AsyncState<T>
): UpdatableState.Data<T> {
    return when (state) {
        is AsyncState.Idle<T> -> copy(state = state.asUnit())
        is AsyncState.Loading<T> -> copy(state = state.asUnit())
        is AsyncState.Error<*> -> copy(state = state.asUnit())
        is AsyncState.Success<T> -> UpdatableState.Data(
            data = state.data,
            state = state.asUnit()
        )
    }
}

inline fun <T : Any, R : Any> UpdatableState<T>.map(block: (T) -> R): UpdatableState<R> {
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

inline fun <T : Any, R : Any> UpdatableState<List<T>>.mapEach(block: (T) -> R): UpdatableState<List<R>> {
    return map {
        it.map(block)
    }
}

inline fun <T : Any, R : Any> UpdatableState.Data<T>.map(block: (T) -> R): UpdatableState.Data<R> {
    return UpdatableState.Data(
        data = block(data),
        state = state,
    )
}

inline fun <T : Any, R : Any> UpdatableState.Data<List<T>>.mapEach(block: (T) -> R): UpdatableState.Data<List<R>> {
    return map {
        it.map(block)
    }
}

inline fun <T : Any, R : Any> UpdatableState<T>.mapNotNull(block: (T) -> R?): UpdatableState<R> {
    return when (this) {
        is UpdatableState.Empty<T> -> {
            @Suppress("UNCHECKED_CAST")
            this as UpdatableState.Empty<R>
        }

        is UpdatableState.Data<T> -> when (val mapped = block(data)) {
            null -> {
                UpdatableState.Empty(state = state)
            }

            else -> {
                UpdatableState.Data(
                    data = mapped,
                    state = state,
                )
            }
        }
    }
}

fun <T : Any> UpdatableState<T>.orDefaultData(data: T): UpdatableState.Data<T> {
    return when (this) {
        is UpdatableState.Empty -> UpdatableState.Data(
            data = data,
            state = state,
        )

        is UpdatableState.Data -> this
    }
}

inline fun <T : Any> UpdatableState<T>.onEmpty(block: UpdatableState<T>.() -> Unit): UpdatableState<T> {
    if (this is UpdatableState.Data) {
        block()
    }
    return this
}

inline fun <T : Any> UpdatableState<T>.onData(block: UpdatableState<T>.(T) -> Unit): UpdatableState<T> {
    if (this is UpdatableState.Data) {
        block(data)
    }
    return this
}

fun <T : Any> Flow<UpdatableState<T>>.onEachEmpty(block: suspend () -> Unit): Flow<UpdatableState<T>> {
    return onEach { state ->
        state.onEmpty { block() }
    }
}

fun <T : Any> Flow<UpdatableState<T>>.onEachData(block: suspend (T) -> Unit): Flow<UpdatableState<T>> {
    return onEach { state ->
        state.onData { block(it) }
    }
}

fun <T : Any, R : Any> Flow<UpdatableState<T>>.mapLatestData(block: suspend (T) -> R): Flow<UpdatableState<R>> {
    return map { updatableState ->
        updatableState.map { data -> block(data) }
    }
}
