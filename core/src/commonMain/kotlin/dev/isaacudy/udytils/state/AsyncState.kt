package dev.isaacudy.udytils.state

import dev.isaacudy.udytils.error.presentableException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

/**
 * The state of an asynchronous operation producing a value of type [T]:
 * [Idle] → [Loading] → [Success] or [Error].
 *
 * `AsyncState` is the library's standard currency for async work. Flows of it are produced by the
 * companion builders ([fromSuspending], [fromFlow], [fromDeferred], [Flow.asAsyncState]) and
 * consumed with the operators in this file ([map], [getOrNull], [onSuccess], [isTerminal], ...).
 * [Success] and [Error] are terminal states; [Idle] means the operation hasn't started, and
 * [Loading] optionally carries progress. When data should survive a reload (e.g. keep showing a
 * list while it refreshes), prefer [UpdatableState].
 *
 * ```
 * AsyncState
 *     .fromSuspending { api.loadUser() }
 *     .collect { state ->
 *         state
 *             .onLoading { progress -> /* show spinner */ }
 *             .onSuccess { user -> /* show user */ }
 *             .onError { error -> /* show error */ }
 *     }
 * ```
 */
sealed class AsyncState<T> {

    /**
     * The operation has not started (or has been reset). All [Idle] instances are equal.
     */
    class Idle<T> : AsyncState<T>() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return true
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }

        override fun toString(): String {
            return "AsyncState.Idle"
        }
    }

    /**
     * The operation is in flight. [progress] is coerced into `0f..1f`; a null [progress] means
     * the operation is [isIndeterminate] (no measurable progress, show an indeterminate spinner).
     */
    class Loading<T>(
        progress: Float? = null
    ) : AsyncState<T>() {

        val progress: Float? = progress?.coerceIn(0f, 1f)
        val isIndeterminate: Boolean = progress == null

        override fun toString(): String {
            if (progress != null) return "AsyncState.Loading(progress=$progress)"
            return "AsyncState.Loading"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Loading<*>

            return progress == other.progress
        }

        override fun hashCode(): Int {
            return progress?.hashCode() ?: 0
        }
    }

    /** Terminal state: the operation completed and produced [data]. */
    data class Success<T>(val data: T) : AsyncState<T>()

    /**
     * Terminal state: the operation failed with [error].
     *
     * A [CancellationException] can never be held as an [Error] — constructing one rethrows the
     * exception, so cancellation always propagates instead of surfacing as an error state.
     */
    data class Error<T>(val error: Throwable) : AsyncState<T>() {
        init {
            if (error is CancellationException) throw error
        }
    }

    companion object {
        /**
         * Creates an [Error] wrapping a [presentableException], an exception that carries a
         * user-facing [title] and [message] which the UI layer can display directly.
         */
        fun <T> presentableError(
            title: String,
            message: String,
            retryable: Boolean = true,
            cause: Throwable? = null
        ): Error<T> {
            return Error(
                presentableException(
                    title = title,
                    message = message,
                    retryable = retryable,
                    cause = cause,
                )
            )
        }
    }
}

/** Wraps this value in [AsyncState.Success]. */
fun <T> T.asAsyncState(): AsyncState<T> {
    return AsyncState.Success(this)
}

/** Wraps this throwable in [AsyncState.Error]. Rethrows if this is a `CancellationException`. */
fun <T> Throwable.asAsyncState(): AsyncState<T> {
    return AsyncState.Error(this)
}

/**
 * Converts a nullable `AsyncState<T?>` into a non-null `AsyncState<T>`, substituting the state
 * produced by [block] when the receiver itself is null or is a [AsyncState.Success] containing
 * null. All other states pass through with their type narrowed.
 */
fun <T : Any> AsyncState<T?>?.mapNull(block: () -> AsyncState<T>): AsyncState<T> {
    return when (this) {
        null -> block()
        is AsyncState.Success -> when (data) {
            null -> block()
            else -> AsyncState.Success(data)
        }

        is AsyncState.Idle -> AsyncState.Idle()
        is AsyncState.Loading -> AsyncState.Loading(progress)
        is AsyncState.Error -> AsyncState.Error(error)
    }
}

/**
 * [mapNull] variant that treats a null receiver or successful-null as an [AsyncState.Error]
 * wrapping a [NullPointerException].
 */
fun <T : Any> AsyncState<T?>?.mapNullAsError(): AsyncState<T> {
    return mapNull { AsyncState.Error(NullPointerException()) }
}

/** [mapNull] variant that treats a null receiver or successful-null as [AsyncState.Idle]. */
fun <T : Any> AsyncState<T?>?.mapNullAsIdle(): AsyncState<T> {
    return mapNull { AsyncState.Idle() }
}

/** The [AsyncState.Success] data, or null for any other state. */
fun <T> AsyncState<T>.getOrNull(): T? {
    return when (this) {
        is AsyncState.Success -> data
        else -> null
    }
}

/**
 * The [AsyncState.Success] data. Throws the wrapped error for [AsyncState.Error], or an
 * [IllegalStateException] when the state is still [AsyncState.Idle] or [AsyncState.Loading].
 */
fun <T> AsyncState<T>.getOrThrow(): T {
    return when (this) {
        is AsyncState.Success -> data
        is AsyncState.Error -> throw error
        else -> throw IllegalStateException()
    }
}

/**
 * Suspends until this flow emits a terminal state (see [isTerminal]), then returns its data or
 * throws its error. Turns a `Flow<AsyncState<T>>` back into an ordinary suspending call.
 */
suspend fun <T> Flow<AsyncState<T>>.getOrThrow(): T {
    return first {
        it.isTerminal()
    }.getOrThrow()
}

/** The [AsyncState.Error] error, or null for any other state. */
fun <T> AsyncState<T>.errorOrNull(): Throwable? {
    return when (this) {
        is AsyncState.Error -> error
        else -> null
    }
}

/**
 * Transforms the data of an [AsyncState.Success] with [block]; all other states pass through
 * unchanged (apart from their type parameter).
 */
inline fun <T, R> AsyncState<T>.map(block: (T) -> R): AsyncState<R> {
    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is AsyncState.Success -> AsyncState.Success(block(data))
        is AsyncState.Error -> AsyncState.Error(error)
        is AsyncState.Loading -> this as AsyncState<R>
        is AsyncState.Idle -> AsyncState.Idle()
    }
}

/**
 * Transforms each element of a successful list with [block]; all other states pass through
 * unchanged. Shorthand for `map { list -> list.map(block) }`.
 */
inline fun <T, R> AsyncState<List<T>>.mapEach(block: (T) -> R): AsyncState<List<R>> {
    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is AsyncState.Success -> AsyncState.Success(data.map { block(it) })
        is AsyncState.Error -> AsyncState.Error(error)
        is AsyncState.Loading -> this as AsyncState<List<R>>
        is AsyncState.Idle -> AsyncState.Idle()
    }
}

/**
 * Discards the data, keeping only the Idle/Loading/Success/Error shape of the state.
 * Identical to [asUnit].
 */
fun AsyncState<*>.toUnit(): AsyncState<Unit> {
    return map { Unit }
}

/** True when this state is [AsyncState.Idle]; a Kotlin contract smart-casts the receiver. */
@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isIdle(): Boolean {
    contract {
        returns(true) implies (this@isIdle is AsyncState.Idle)
    }
    return this is AsyncState.Idle
}

/** This state as [AsyncState.Idle], or null when it is any other state. */
fun <T> AsyncState<T>.asIdleOrNull(): AsyncState.Idle<T>? {
    if (isIdle()) return this
    return null
}

/** True when this state is [AsyncState.Loading]; a Kotlin contract smart-casts the receiver. */
@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isLoading(): Boolean {
    contract {
        returns(true) implies (this@isLoading is AsyncState.Loading)
    }
    return this is AsyncState.Loading
}

/** This state as [AsyncState.Loading], or null when it is any other state. */
fun <T> AsyncState<T>.asLoadingOrNull(): AsyncState.Loading<T>? {
    if (isLoading()) return this
    return null
}

/** True when this state is [AsyncState.Success]; a Kotlin contract smart-casts the receiver. */
@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is AsyncState.Success)
    }
    return this is AsyncState.Success
}

/** This state as [AsyncState.Success], or null when it is any other state. */
fun <T> AsyncState<T>.asSuccessOrNull(): AsyncState.Success<T>? {
    if (isSuccess()) return this
    return null
}

/** True when this state is [AsyncState.Error]; a Kotlin contract smart-casts the receiver. */
@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isError(): Boolean {
    contract {
        returns(true) implies (this@isError is AsyncState.Error)
    }
    return this is AsyncState.Error
}

/** This state as [AsyncState.Error], or null when it is any other state. */
fun <T> AsyncState<T>.asErrorOrNull(): AsyncState.Error<T>? {
    if (isError()) return this
    return null
}

/**
 * True when this state is [AsyncState.Success] or [AsyncState.Error] — the operation has
 * finished and no further transitions are expected. A contract smart-casts the receiver to
 * exclude [AsyncState.Idle] and [AsyncState.Loading].
 */
@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isTerminal(): Boolean {
    contract {
        returns(true) implies (this@isTerminal !is AsyncState.Idle)
        returns(true) implies (this@isTerminal !is AsyncState.Loading)
    }
    return when (this) {
        is AsyncState.Success, is AsyncState.Error -> true
        else -> false
    }
}

/** Runs [block] when this state is [AsyncState.Idle]; returns `this` for chaining. */
inline fun <T> AsyncState<T>.onIdle(block: () -> Unit): AsyncState<T> {
    if (this is AsyncState.Idle) {
        block()
    }
    return this
}

/**
 * Runs [block] with the current progress (null when indeterminate) when this state is
 * [AsyncState.Loading]; returns `this` for chaining.
 */
inline fun <T> AsyncState<T>.onLoading(block: (Float?) -> Unit): AsyncState<T> {
    if (this is AsyncState.Loading) {
        block(progress)
    }
    return this
}

/** Runs [block] with the data when this state is [AsyncState.Success]; returns `this` for chaining. */
inline fun <T> AsyncState<T>.onSuccess(block: (T) -> Unit): AsyncState<T> {
    if (this is AsyncState.Success) {
        block(data)
    }
    return this
}

/** Runs [block] with the error when this state is [AsyncState.Error]; returns `this` for chaining. */
inline fun <T> AsyncState<T>.onError(block: (Throwable) -> Unit): AsyncState<T> {
    if (this is AsyncState.Error) {
        block(error)
    }
    return this
}

/**
 * Flow variant of [mapNullAsError]: replaces each [AsyncState.Success] emission containing null
 * with an [AsyncState.Error] wrapping a [NullPointerException]; other emissions pass through.
 */
fun <T : Any> Flow<AsyncState<T?>>.mapNullToError(): Flow<AsyncState<T>> {
    return map { state ->
        when (state) {
            is AsyncState.Success -> when (state.data) {
                null -> AsyncState.Error(NullPointerException())
                else -> AsyncState.Success(state.data)
            }

            else -> {
                @Suppress("UNCHECKED_CAST")
                state as AsyncState<T>
            }
        }
    }
}

/**
 * Transforms the data of each [AsyncState.Success] emission with the suspending [block]; other
 * emissions pass through unchanged.
 */
fun <T, R> Flow<AsyncState<T>>.mapLatestSuccess(block: suspend (T) -> R): Flow<AsyncState<R>> {
    return map { asyncState ->
        asyncState.map { data -> block(data) }
    }
}

/** Runs [block] for each [AsyncState.Idle] emission; all emissions pass through untouched. */
fun <T> Flow<AsyncState<T>>.onEachIdle(block: suspend () -> Unit): Flow<AsyncState<T>> {
    return onEach { asyncState ->
        asyncState.onIdle { block() }
    }
}

/**
 * Runs [block] with the progress (null when indeterminate) for each [AsyncState.Loading]
 * emission; all emissions pass through untouched.
 */
fun <T> Flow<AsyncState<T>>.onEachLoading(block: suspend (Float?) -> Unit): Flow<AsyncState<T>> {
    return onEach { asyncState ->
        asyncState.onLoading { block(it) }
    }
}

/** Runs [block] with the data for each [AsyncState.Success] emission; all emissions pass through. */
fun <T> Flow<AsyncState<T>>.onEachSuccess(block: suspend (T) -> Unit): Flow<AsyncState<T>> {
    return onEach { asyncState ->
        asyncState.onSuccess { block(it) }
    }
}

/** Runs [block] with the error for each [AsyncState.Error] emission; all emissions pass through. */
fun <T> Flow<AsyncState<T>>.onEachError(block: suspend (Throwable) -> Unit): Flow<AsyncState<T>> {
    return onEach { asyncState ->
        asyncState.onError { block(it) }
    }
}

/**
 * Switches to the flow produced by [block] whenever an [AsyncState.Success] arrives, wrapping
 * its emissions via [AsyncState.fromFlow]; non-success emissions pass through as-is. Uses
 * `flatMapLatest` semantics, so a new upstream emission cancels the previous inner flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> Flow<AsyncState<T>>.flatMapSuccess(block: suspend (T) -> Flow<R>): Flow<AsyncState<R>> {
    return flatMapLatest { asyncState ->
        @Suppress("UNCHECKED_CAST")
        when (asyncState) {
            is AsyncState.Success<T> -> AsyncState.fromFlow(block(asyncState.data))
            else -> flowOf(asyncState as AsyncState<R>)
        }
    }
}

/**
 * Discards the data, keeping only the Idle/Loading/Success/Error shape of the state.
 * Identical to [toUnit].
 */
fun AsyncState<*>.asUnit(): AsyncState<Unit> {
    return map { Unit }
}
