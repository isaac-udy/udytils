package dev.isaacudy.udytils.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Observable UI state owned by a [ViewModel]: readable and collectable from anywhere, but
 * writable only from within the owning ViewModel.
 *
 * Wraps a [MutableStateFlow] and exposes [value] plus [collectAsState] /
 * [collectAsStateWithLifecycle] for Compose, while the [update] members require a [ViewModel]
 * context receiver so external code can never mutate the state. Create with [viewModelState],
 * or with [ViewModelState.Companion.build] when the initial state needs to reference the state
 * itself.
 *
 * ```
 * class ProfileViewModel : ViewModel() {
 *     val state = viewModelState(ProfileState())
 *
 *     fun onNameChanged(name: String) {
 *         state.update { copy(name = name) }
 *     }
 * }
 *
 * @Composable
 * fun ProfileScreen(viewModel: ProfileViewModel) {
 *     val state by viewModel.state.collectAsState()
 *     // ...
 * }
 * ```
 */
class ViewModelState<T>(
    @PublishedApi
    internal val stateFlow: MutableStateFlow<T>
) {
    /** The current state value. */
    val value: T get() = stateFlow.value

    /**
     * Replaces the current state. The ViewModel context parameter is unused, but it exists so
     * that update can only be called within a ViewModel's context.
     */
    context(viewModel: ViewModel)
    fun update(value: T) {
        stateFlow.value = value
    }

    /**
     * Transforms the current state; [block] receives the current value as its receiver. The
     * ViewModel context parameter is unused, but it exists so that update can only be called
     * within a ViewModel's context.
     */
    context(viewModel: ViewModel)
    inline fun update(block: T.() -> T) {
        stateFlow.value = block(stateFlow.value)
    }

    /** Collects this state in composition, recomposing whenever the value changes. */
    @Composable
    fun collectAsState(): State<T> {
        return stateFlow.collectAsState()
    }

    /**
     * Lifecycle-aware collection: stops collecting while [lifecycleOwner] is below
     * [minActiveState], resuming when it returns. Prefer this over [collectAsState] on Android,
     * where collection should pause while the app is in the background.
     */
    @Composable
    fun collectAsStateWithLifecycle(
        lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
        minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
        context: CoroutineContext = EmptyCoroutineContext
    ): State<T> {
        return stateFlow.collectAsStateWithLifecycle(
            lifecycleOwner = lifecycleOwner,
            minActiveState = minActiveState,
            context = context,
        )
    }

    companion object {
        /**
         * Creates a [ViewModelState] whose initial value can reference the state itself through
         * [Builder.state] — useful when the initial state contains callbacks that update the
         * state. Updates dispatched before construction completes are queued and applied as
         * soon as the state is bound.
         *
         * ```
         * val state = ViewModelState.build {
         *     ScreenState(
         *         onRefresh = { state.update { copy(isRefreshing = true) } }
         *     )
         * }
         * ```
         */
        context(viewModel: ViewModel)
        fun <T> build(
            block: Builder<T>.() -> T
        ) : ViewModelState<T> {
            val builder = Builder<T>(viewModel)
            return ViewModelState(
                MutableStateFlow(block(builder))
            ).also { state ->
                builder.bind(state)
            }
        }
    }

    /**
     * Receiver for [ViewModelState.Companion.build]: exposes [state], a reference to the
     * state being constructed that is safe to capture in lambdas inside the initial value.
     */
    class Builder<T> internal constructor(
        private val viewModel: ViewModel,
    ) {
        private var boundState: ViewModelState<T>? = null
        private val queuedUpdates: MutableList<T.() -> T> = mutableListOf()
        val state: StateReference = StateReference()

        internal fun bind(state: ViewModelState<T>) {
            require(this.boundState == null)
            this.boundState = state
            val queued = queuedUpdates.toList()
            queuedUpdates.clear()
            viewModel.run {
                queued.forEach { queuedUpdate ->
                    state.update(queuedUpdate)
                }
            }
        }

        /**
         * Handle to the [ViewModelState] being built. Updates made before the state exists are
         * queued and applied immediately once construction completes.
         */
        inner class StateReference {
            fun update(block: T.() -> T) {
                val current = boundState
                if (current == null) {
                    queuedUpdates.add(block)
                    return
                }
                viewModel.run {
                    current.update { block(this) }
                }
            }
        }
    }
}

/**
 * Creates a [ViewModelState] with [initialState]. Must be called in the context of a
 * [ViewModel] — typically as a property initializer of the ViewModel that owns the state.
 */
context(viewModel: ViewModel)
fun <T> viewModelState(
    initialState: T
) : ViewModelState<T> {
    return ViewModelState(MutableStateFlow(initialState))
}
