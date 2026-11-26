package dev.isaacudy.udytils.trace

import kotlin.time.TimeSource

data class TraceId(val high: Long, val low: Long) {
    override fun toString(): String = high.toULong().toString(16).padStart(16, '0') + low.toULong().toString(16).padStart(16, '0')
}

sealed interface Outcome {
    data object Running : Outcome
    data object Returned : Outcome
    data object Cancelled : Outcome
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
) {
    private val start = TimeSource.Monotonic.markNow()

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

    override fun toString(): String = "Span(${site.name}, trace=$traceId, outcome=$outcome)"
}
