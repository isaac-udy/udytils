package dev.isaacudy.udytils.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import dev.isaacudy.udytils.error.presentableException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

@Stable
@Immutable
sealed class AsyncState<T> {

    @Stable
    @Immutable
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

    @Stable
    @Immutable
    class Loading<T> : AsyncState<T>() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return true
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }

        override fun toString(): String {
            return "AsyncState.Loading"
        }
    }

    @Stable
    @Immutable
    data class Success<T>(val data: T) : AsyncState<T>()

    @Stable
    @Immutable
    data class Error<T>(val error: Throwable) : AsyncState<T>() {
        init {
            if (error is CancellationException) throw error
        }
    }

    companion object {
        fun <T> presentableError(
            title: String,
            message: String,
            retryable: Boolean = true,
        ) : Error<T> {
            return Error(
                presentableException(
                    title = title,
                    message = message,
                    retryable = retryable,
                )
            )
        }
    }
}

fun <T> T.asAsyncState(): AsyncState<T> {
    return AsyncState.Success(this)
}

fun <T> Throwable.asAsyncState(): AsyncState<T> {
    return AsyncState.Error(this)
}

fun <T: Any> AsyncState<T?>?.mapNull(block: () -> AsyncState<T>): AsyncState<T> {
    return when (this) {
        null -> block()
        is AsyncState.Success -> when(data) {
            null -> block()
            else -> AsyncState.Success(data)
        }
        else -> AsyncState.Error(NullPointerException())
    }
}

fun <T: Any> AsyncState<T?>?.mapNullAsError(): AsyncState<T> {
    return mapNull { AsyncState.Error(NullPointerException()) }
}

fun <T: Any> AsyncState<T?>?.mapNullAsIdle(): AsyncState<T> {
    return mapNull { AsyncState.Idle() }
}

fun <T> AsyncState<T>.getOrNull(): T? {
    return when (this) {
        is AsyncState.Success -> data
        else -> null
    }
}

fun <T> AsyncState<T>.getOrThrow(): T {
    return when (this) {
        is AsyncState.Success -> data
        is AsyncState.Error -> throw error
        else -> throw IllegalStateException()
    }
}


fun <T> AsyncState<T>.errorOrNull(): Throwable? {
    return when (this) {
        is AsyncState.Error -> error
        else -> null
    }
}

inline fun <T, R> AsyncState<T>.map(block: (T) -> R): AsyncState<R> {
    return when (this) {
        is AsyncState.Success -> AsyncState.Success(block(data))
        is AsyncState.Error -> AsyncState.Error(error)
        is AsyncState.Loading -> AsyncState.Loading()
        is AsyncState.Idle -> AsyncState.Idle()
    }
}

fun AsyncState<*>.toUnit(): AsyncState<Unit> {
    return map { Unit }
}

@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isIdle(): Boolean {
    contract {
        returns(true) implies (this@isIdle is AsyncState.Idle)
    }
    return this is AsyncState.Idle
}

@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isLoading(): Boolean {
    contract {
        returns(true) implies (this@isLoading is AsyncState.Loading)
    }
    return this is AsyncState.Loading
}

@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is AsyncState.Success)
    }
    return this is AsyncState.Success
}

@OptIn(ExperimentalContracts::class)
fun <T>  AsyncState<T>.isError(): Boolean {
    contract {
        returns(true) implies (this@isError is AsyncState.Error)
    }
    return this is AsyncState.Error
}

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

inline fun <T> AsyncState<T>.onIdle(block: () -> Unit): AsyncState<T> {
    if (this is AsyncState.Idle) {
        block()
    }
    return this
}

inline fun <T> AsyncState<T>.onLoading(block: () -> Unit): AsyncState<T> {
    if (this is AsyncState.Loading) {
        block()
    }
    return this
}

inline fun <T> AsyncState<T>.onSuccess(block: (T) -> Unit): AsyncState<T> {
    if (this is AsyncState.Success) {
        block(data)
    }
    return this
}

inline fun <T> AsyncState<T>.onError(block: (Throwable) -> Unit): AsyncState<T> {
    if (this is AsyncState.Error) {
        block(error)
    }
    return this
}

fun <T: Any> Flow<AsyncState<T?>>.mapNullToError(): Flow<AsyncState<T>> {
    return map { state ->
        when (state) {
            is AsyncState.Success -> when(state.data) {
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

fun <T, R> Flow<AsyncState<T>>.mapLatestSuccess(block: suspend (T) -> R): Flow<AsyncState<R>> {
    return map { asyncState ->
        asyncState.map { data -> block(data) }
    }
}

fun <T> Flow<AsyncState<T>>.onEachIdle(block: suspend () -> Unit): Flow<AsyncState<T>> {
    return onEach { asyncState ->
        asyncState.onIdle { block() }
    }
}

fun <T> Flow<AsyncState<T>>.onEachLoading(block: suspend () -> Unit): Flow<AsyncState<T>> {
    return onEach { asyncState ->
        asyncState.onLoading { block() }
    }
}

fun <T> Flow<AsyncState<T>>.onEachSuccess(block: suspend (T) -> Unit): Flow<AsyncState<T>> {
    return onEach { asyncState ->
        asyncState.onSuccess { block(it) }
    }
}

fun <T> Flow<AsyncState<T>>.onEachError(block: suspend (Throwable) -> Unit): Flow<AsyncState<T>> {
    return onEach { asyncState ->
        asyncState.onError { block(it) }
    }
}

fun AsyncState<*>.asUnit(): AsyncState<Unit> {
    return map { Unit }
}