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
    private val linkResolvers = AtomicReference(emptyList<(Any) -> Span?>())
    private val visitSites = AtomicReference(emptyMap<String, TraceSite>())

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

    /**
     * Adds [resolver], which returns the span that a span starting a new trace links to, from the
     * receiver of the traced call, such as the screen visit of a ViewModel. [resolver] runs in the
     * traced code and must return null instead of throwing. Closing the result removes [resolver].
     */
    fun linkRoots(resolver: (receiver: Any) -> Span?): AutoCloseable {
        update(linkResolvers) { it + resolver }
        return AutoCloseable { update(linkResolvers) { current -> current.filterNot { it === resolver } } }
    }

    internal fun linksFor(receiver: Any?): List<Span> {
        if (receiver == null) return emptyList()
        val resolvers = linkResolvers.load()
        if (resolvers.isEmpty()) return emptyList()
        return resolvers.mapNotNull { it(receiver) }
    }

    /**
     * Opens a [SpanKind.Visit] span named [name] that starts a new trace, such as one visit of a
     * screen; [receiver], such as the screen's navigation key, is the instance that rules on the
     * visit receive. Returns null when no sink records visits named [name]. [endVisit] ends the span.
     */
    @OptIn(TraceProbeApi::class)
    fun startVisit(name: String, receiver: Any? = null): Span? {
        val site = visitSites.load()[name] ?: run {
            val created = TraceSite(visitSiteId(name), visitSiteId(name), name, isVisit = true)
            update(visitSites) { if (name in it) it else it + (name to created) }
            visitSites.load().getValue(name)
        }
        if (site.flags and SiteFlags.RECORD == 0) return null
        val span = newSpan(site, parent = null, previous = null, reported = true, kind = SpanKind.Visit)
        span.receiver = receiver
        return span.also(::notifyStart)
    }

    /** Ends a span that [startVisit] opened. */
    fun endVisit(span: Span) {
        span.end()
        notifyEnd(span)
        span.receiver = null
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

/** The FNV-1a hash of [name]; visit sites have no compiler-generated ID. */
private fun visitSiteId(name: String): Long {
    var hash = -0x340d631b7bdddcdbL
    for (char in "visit:$name") {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash
}

/**
 * The current span; when no span is current, the span that an instrumented caller in [caller]'s
 * coroutine handed to the uninstrumented code that is running; then the [TraceContext] of [caller].
 */
private fun traceLaunchContext(caller: CoroutineContext?): CoroutineContext {
    val span = CurrentSpan.value?.takeIf { it.reported }
        ?: caller?.let(::handoffFor)
        ?: caller?.get(TraceContext)?.span
        ?: return EmptyCoroutineContext
    return TraceContext(span)
}
