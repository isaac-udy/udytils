package dev.isaacudy.udytils.trace

import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

data class TraceId(val high: Long, val low: Long) {
    override fun toString(): String = high.toULong().toString(16).padStart(16, '0') + low.toULong().toString(16).padStart(16, '0')
}

enum class SpanKind {
    /** One call of the site's function. */
    Call,

    /** One collection of the Flow that the site's function returns. */
    Collect,

    /** From the start of a collection to the collection's first value. */
    FirstValue,

    /** One construction of an instance: the class's property initializers and `init` blocks. */
    Construction,

    /** One visit of a lifecycle that no function call covers, such as a screen, from [Trace.startVisit] to [Trace.endVisit]. */
    Visit,
}

/** How a span ended. Metrics that count failures count [Threw] only. */
sealed interface Outcome {
    data object Running : Outcome

    /** The call returned, or the collection received every value the upstream emitted. */
    data object Returned : Outcome

    /**
     * The call or collection ended with a `CancellationException`. Three cases report [Cancelled]:
     * - the coroutine was cancelled, for example when `JobManager.launchReplacing` replaces the job;
     * - a [SpanKind.Collect] collection stopped early because the collector requires no further
     *   values (`first()`, `take(n)`, `takeWhile`); the `emissions` attribute holds the values received;
     * - a timeout from `withTimeout`.
     *
     * kotlinx.coroutines reports all three with a `CancellationException` and does not expose the
     * early-stop exception type, so the runtime cannot distinguish them.
     */
    data object Cancelled : Outcome

    /** The call or collection threw an exception other than `CancellationException`. */
    data class Threw(val error: Throwable) : Outcome
}

class Span internal constructor(
    val site: TraceSite,
    val traceId: TraceId,
    val spanId: Long,
    val parent: Span?,
    /** The current span to restore when a non-suspend call ends; differs from [parent] for a root. */
    internal val previous: Span?,
    /** False for a root site that no sink records: the span only isolates its callees from the caller's trace. */
    internal val reported: Boolean,
    val kind: SpanKind = SpanKind.Call,
    private val start: ValueTimeMark = TimeSource.Monotonic.markNow(),
    /** True for a parent from another process, which this process never starts or ends. */
    val isRemote: Boolean = false,
) {
    /** Values that the runtime records on the span, such as [EMISSIONS] for a [SpanKind.Collect] span. */
    val attributes: Map<String, Any> get() = recorded

    private val recorded = mutableMapOf<String, Any>()

    internal fun record(key: String, value: Any) {
        recorded[key] = value
    }

    internal val startMark: ValueTimeMark get() = start

    /** The instance whose call, construction or visit this span records; cleared when the span's sinks have seen its end. */
    internal var receiver: Any? = null

    /** The call's argument values when a sink sets [SiteFlags.ARGS] for the site; cleared with [receiver]. */
    internal var arguments: Array<Any?>? = null

    /** The value the call returned; cleared with [receiver]. */
    internal var result: Any? = null

    internal fun clearCaptured() {
        receiver = null
        arguments = null
        result = null
    }

    /** Spans in other traces that this span relates to: for a span that starts a trace, the spans that [Trace.linkRoots] resolved. */
    var links: List<Span> = emptyList()
        internal set

    var outcome: Outcome = Outcome.Running
        internal set

    /** The span's duration in nanoseconds; -1 while [outcome] is [Outcome.Running]. */
    var durationNanos: Long = -1
        private set

    val parentSpanId: Long? get() = parent?.spanId

    internal fun end() {
        durationNanos = start.elapsedNow().inWholeNanoseconds
        if (outcome == Outcome.Running) outcome = Outcome.Returned
    }

    override fun toString(): String = "Span(${site.name}, kind=$kind, trace=$traceId, outcome=$outcome)"

    companion object {
        /** The number of values a [SpanKind.Collect] span's collection received. */
        const val EMISSIONS: String = "emissions"
    }
}
