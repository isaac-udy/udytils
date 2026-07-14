package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Data that survives refreshes: either [Empty] or [Data], each carrying the [AsyncState] of the
 * most recent update operation in [state].
 *
 * Where a plain `AsyncState<T>` loses its data whenever a new load starts, `UpdatableState`
 * separates "what data do we currently have" from "what is the current operation doing" — so a
 * screen can keep rendering its existing items while a refresh is loading or has failed. Build
 * flows of it with [UpdatableState.Companion.fromFlow] / [UpdatableState.Companion.fromSuspending],
 * or fold individual [AsyncState] emissions into an existing value with [updateFrom].
 *
 * ```
 * UpdatableState
 *     .fromSuspending(initialData = cachedUsers) { api.loadUsers() }
 *     .collect { state ->
 *         showUsers(state.dataOrEmpty)              // stale data stays visible
 *         showRefreshing(state.state.isLoading())   // while the update runs
 *     }
 * ```
 */
sealed interface UpdatableState<T : Any> {
    /**
     * The state of the most recent update operation. Successful data is folded into [Data.data],
     * so this is always an `AsyncState<Unit>` describing only the operation's phase.
     */
    val state: AsyncState<Unit>

    /** No data is available (nothing loaded yet, or the last value was mapped away). */
    data class Empty<T : Any>(
        override val state: AsyncState<Unit> = AsyncState.Idle()
    ) : UpdatableState<T>

    /** Holds the most recently loaded [data]; [state] describes any update currently in flight. */
    data class Data<T : Any>(
        val data: T,
        override val state: AsyncState<Unit> = AsyncState.Idle()
    ) : UpdatableState<T>

    companion object
}

/** True when this state is [UpdatableState.Empty]; a Kotlin contract smart-casts the receiver. */
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

/** The current data, or null when this state is [UpdatableState.Empty]. */
val <T : Any> UpdatableState<T>.dataOrNull: T?
    get() = when (this) {
        is UpdatableState.Data<T> -> data
        is UpdatableState.Empty<T> -> null
    }

/** The current list, or an empty list when this state is [UpdatableState.Empty]. */
val <T : Any> UpdatableState<List<T>>.dataOrEmpty: List<T>
    get() = when (this) {
        is UpdatableState.Data<List<T>> -> data
        is UpdatableState.Empty<List<T>> -> emptyList()
    }

/** The current map, or an empty map when this state is [UpdatableState.Empty]. */
val <K, V> UpdatableState<Map<K, V>>.dataOrEmpty: Map<K, V>
    get() = when (this) {
        is UpdatableState.Data<Map<K, V>> -> data
        is UpdatableState.Empty<Map<K, V>> -> emptyMap()
    }


/**
 * Folds an [AsyncState] emission into this state: [AsyncState.Success] replaces the data (an
 * [UpdatableState.Empty] becomes [UpdatableState.Data]), while Idle/Loading/Error only update
 * [UpdatableState.state], preserving whatever data is already held.
 */
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

/**
 * As [updateFrom], but statically preserves the [UpdatableState.Data] type — data can never be
 * lost by folding in an [AsyncState].
 */
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

/**
 * Transforms the data when present; [UpdatableState.Empty] passes through, and the update
 * [UpdatableState.state] is preserved either way.
 */
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

/**
 * Transforms each element of the held list with [block]; [UpdatableState.Empty] passes through.
 * Shorthand for `map { list -> list.map(block) }`.
 */
inline fun <T : Any, R : Any> UpdatableState<List<T>>.mapEach(block: (T) -> R): UpdatableState<List<R>> {
    return map {
        it.map(block)
    }
}

/** As [map], but statically preserves the [UpdatableState.Data] type. */
inline fun <T : Any, R : Any> UpdatableState.Data<T>.map(block: (T) -> R): UpdatableState.Data<R> {
    return UpdatableState.Data(
        data = block(data),
        state = state,
    )
}

/** As [mapEach], but statically preserves the [UpdatableState.Data] type. */
inline fun <T : Any, R : Any> UpdatableState.Data<List<T>>.mapEach(block: (T) -> R): UpdatableState.Data<List<R>> {
    return map {
        it.map(block)
    }
}

/**
 * Like [map], but a null result from [block] converts [UpdatableState.Data] into
 * [UpdatableState.Empty], preserving the update [UpdatableState.state].
 */
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

/**
 * Substitutes [data] when this state is [UpdatableState.Empty], guaranteeing a
 * [UpdatableState.Data] result while preserving the update [UpdatableState.state].
 */
fun <T : Any> UpdatableState<T>.orDefaultData(data: T): UpdatableState.Data<T> {
    return when (this) {
        is UpdatableState.Empty -> UpdatableState.Data(
            data = data,
            state = state,
        )

        is UpdatableState.Data -> this
    }
}

/** Runs [block] when this state is [UpdatableState.Empty]; returns `this` for chaining. */
inline fun <T : Any> UpdatableState<T>.onEmpty(block: UpdatableState<T>.() -> Unit): UpdatableState<T> {
    if (this is UpdatableState.Empty) {
        block()
    }
    return this
}

/** Runs [block] with the data when this state is [UpdatableState.Data]; returns `this` for chaining. */
inline fun <T : Any> UpdatableState<T>.onData(block: UpdatableState<T>.(T) -> Unit): UpdatableState<T> {
    if (this is UpdatableState.Data) {
        block(data)
    }
    return this
}

/** Runs [block] for each [UpdatableState.Empty] emission; all emissions pass through. */
fun <T : Any> Flow<UpdatableState<T>>.onEachEmpty(block: suspend () -> Unit): Flow<UpdatableState<T>> {
    return onEach { state ->
        state.onEmpty { block() }
    }
}

/** Runs [block] with the data for each [UpdatableState.Data] emission; all emissions pass through. */
fun <T : Any> Flow<UpdatableState<T>>.onEachData(block: suspend (T) -> Unit): Flow<UpdatableState<T>> {
    return onEach { state ->
        state.onData { block(it) }
    }
}

/**
 * Transforms the data of each [UpdatableState.Data] emission with the suspending [block];
 * [UpdatableState.Empty] emissions pass through unchanged.
 */
fun <T : Any, R : Any> Flow<UpdatableState<T>>.mapLatestData(block: suspend (T) -> R): Flow<UpdatableState<R>> {
    return map { updatableState ->
        updatableState.map { data -> block(data) }
    }
}
