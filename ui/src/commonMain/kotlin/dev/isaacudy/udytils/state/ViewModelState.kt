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
}

context(viewModel: ViewModel)
fun <T> viewModelState(
    initialState: T
) : ViewModelState<T> {
    return ViewModelState(MutableStateFlow(initialState))
}

