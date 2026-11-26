package dev.isaacudy.udytils.coroutines

import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Context elements that udytils adds to the coroutines it launches, such as [JobManager] jobs.
 * A tracing runtime installs [provider] so a launched coroutine inherits the caller's trace.
 */
object LaunchContext {
    /** Returns the elements for a coroutine launched by code running in `caller`; `caller` is null in non-suspend code. */
    @Volatile
    var provider: (caller: CoroutineContext?) -> CoroutineContext = { EmptyCoroutineContext }

    fun forLaunch(caller: CoroutineContext? = null): CoroutineContext = provider(caller)
}
