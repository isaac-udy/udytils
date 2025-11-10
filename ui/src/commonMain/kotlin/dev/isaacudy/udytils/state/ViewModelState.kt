package dev.isaacudy.udytils.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class ViewModelState<T>(
    @PublishedApi
    internal val stateFlow: MutableStateFlow<T>
) {
    val value: T get() = stateFlow.value

    // The ViewModel context parameter is unused, but it exists
    // so that update can only be called within a ViewModel's context
    context(viewModel: ViewModel)
    fun update(value: T) {
        stateFlow.value = value
    }

    // The ViewModel context parameter is unused, but it exists
    // so that update can only be called within a ViewModel's context
    context(viewModel: ViewModel)
    inline fun update(block: T.() -> T) {
        stateFlow.value = block(stateFlow.value)
    }

    @Composable
    fun collectAsState(): State<T> {
        return stateFlow.collectAsState()
    }

    companion object {
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

        inner class StateReference {
            fun update(block: T.() -> T) {
                val current = boundState
                if (current == null) {
                    queuedUpdates.plus(block)
                    return
                }
                viewModel.run {
                    current.update { block(this) }
                }
            }
        }
    }
}

context(viewModel: ViewModel)
fun <T> viewModelState(
    initialState: T
) : ViewModelState<T> {
    return ViewModelState(MutableStateFlow(initialState))
}
