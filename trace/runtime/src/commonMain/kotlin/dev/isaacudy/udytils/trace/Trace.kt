@file:OptIn(ExperimentalAtomicApi::class)

package dev.isaacudy.udytils.trace

import dev.isaacudy.udytils.coroutines.LaunchContext
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Trace {
    init {
        LaunchContext.provider = { caller -> traceLaunchContext(caller) }
    }

    private val sites = AtomicReference(emptyList<TraceSite>())
    private val sinks = AtomicReference(emptyList<SpanSink>())

    internal fun activeSinks(): List<SpanSink> = sinks.load()

    /** Adds [sink] and recomputes the flags of every registered site. Closing the result removes [sink]. */
    fun addSink(sink: SpanSink): AutoCloseable {
        update(sinks) { it + sink }
        refreshFlags()
        return AutoCloseable {
            update(sinks) { current -> current.filterNot { it === sink } }
            refreshFlags()
        }
    }

    /** The span of the instrumented call running on this thread, or null. */
    val currentSpan: Span? get() = CurrentSpan.value

    internal fun register(site: TraceSite): Int {
        update(sites) { it + site }
        return flagsOf(site)
    }

    private fun refreshFlags() {
        for (site in sites.load()) site.flags = flagsOf(site)
    }

    private fun flagsOf(site: TraceSite): Int = sinks.load().fold(0) { flags, sink -> flags or sink.flagsFor(site) }

    private inline fun <T> update(reference: AtomicReference<T>, transform: (T) -> T) {
        while (true) {
            val current = reference.load()
            if (reference.compareAndSet(current, transform(current))) return
        }
    }
}

/** The current span, or when no span is current, the [TraceContext] of the caller's coroutine. */
private fun traceLaunchContext(caller: CoroutineContext?): CoroutineContext {
    val span = CurrentSpan.value?.takeIf { it.reported } ?: caller?.get(TraceContext)?.span ?: return EmptyCoroutineContext
    return TraceContext(span)
}
