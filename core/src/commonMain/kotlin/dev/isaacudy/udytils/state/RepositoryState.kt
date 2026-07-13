package dev.isaacudy.udytils.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * A [StateFlow] whose value can only be updated from within its owning repository of type [R].
 *
 * Consumers are free to collect and read the state, but the [update] members require a context
 * receiver of the repository type, so writes are compile-time restricted to code running inside
 * the repository. This is the repository-layer counterpart of the ui module's `ViewModelState`;
 * create instances with [repositoryState].
 */
class RepositoryState<R : Any, T>(
    @PublishedApi
    internal val stateFlow: MutableStateFlow<T>
) : StateFlow<T> by stateFlow {
    /**
     * Replaces the current value. The repository context parameter is unused, but it exists so
     * that update can only be called within the owning repository's context.
     */
    context(repository: R)
    fun update(value: T) {
        stateFlow.value = value
    }

    /**
     * Atomically transforms the current value; [block] receives the current value as its
     * receiver. The repository context parameter is unused, but it exists so that update can
     * only be called within the owning repository's context.
     */
    context(repository: R)
    inline fun update(block: T.() -> T) {
        stateFlow.update { block(it) }
    }
}

/**
 * Creates a [RepositoryState] owned by the current repository context.
 *
 * ```
 * class UserRepository {
 *     val users: RepositoryState<UserRepository, List<User>> = repositoryState(emptyList())
 *
 *     suspend fun refresh() {
 *         users.update(api.loadUsers())
 *     }
 * }
 * ```
 */
context(repository: R)
fun <R: Any, T> repositoryState(
    initialState: T
) : RepositoryState<R, T> {
    return RepositoryState(MutableStateFlow(initialState))
}
