package dev.isaacudy.udytils.trace

import kotlin.coroutines.CoroutineContext

/** The W3C `traceparent` header value of this span: version, trace ID, parent span ID and the sampled flag. */
val Span.traceparent: String
    get() = "00-$traceId-${spanId.toULong().toString(16).padStart(16, '0')}-01"

/**
 * A [TraceContext] whose span is the remote parent that [traceparent] names, or null when
 * [traceparent] is not a version-00 W3C value. A coroutine started with the context continues the
 * caller's trace: its spans take the remote trace ID and the remote span as their parent.
 */
fun remoteParent(traceparent: String): TraceContext? {
    val parts = traceparent.trim().split('-')
    if (parts.size != 4 || parts[0] != "00" || parts[1].length != 32 || parts[2].length != 16) return null
    val high = parts[1].substring(0, 16).toULongOrNull(16) ?: return null
    val low = parts[1].substring(16).toULongOrNull(16) ?: return null
    val spanId = parts[2].toULongOrNull(16) ?: return null
    if (high == 0uL && low == 0uL || spanId == 0uL) return null
    val span = Span(
        site = remoteSite,
        traceId = TraceId(high.toLong(), low.toLong()),
        spanId = spanId.toLong(),
        parent = null,
        previous = null,
        reported = true,
        isRemote = true,
    )
    return TraceContext(span)
}

/** The span of [context]'s [TraceContext], or the span that an instrumented caller handed to the running code. */
fun Trace.spanOf(context: CoroutineContext): Span? =
    currentSpan?.takeIf { it.reported } ?: handoffFor(context) ?: context[TraceContext]?.span

@OptIn(TraceProbeApi::class)
private val remoteSite by lazy { TraceSite(siteId = 0, declarationId = 0, name = "remote") }
