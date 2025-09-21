package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class RepositoryState<R : Any, T>(
    @PublishedApi
    internal val stateFlow: MutableStateFlow<T>
) : StateFlow<T> by stateFlow {
    // The ViewModel context parameter is unused, but it exists
    // so that update can only be called within a ViewModel's context
    context(repository: R)
    fun update(value: T) {
        stateFlow.value = value
    }

    // The ViewModel context parameter is unused, but it exists
    // so that update can only be called within a ViewModel's context
    context(repository: R)
    inline fun update(block: T.() -> T) {
        stateFlow.update { block(it) }
    }
}

context(repository: R)
fun <R: Any, T> repositoryState(
    initialState: T
) : RepositoryState<R, T> {
    return RepositoryState(MutableStateFlow(initialState))
}
