package dev.isaacudy.udytils.trace

import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** Opens a span for [site]; returns null when no sink selects [site]. */
@TraceProbeApi
fun traceEnter(site: TraceSite): Span? {
    val flags = site.flags
    if (flags == 0) return null
    val parent = if (flags and SiteFlags.ROOT != 0) null else CurrentSpan.value
    val span = Span(
        site = site,
        traceId = parent?.traceId ?: TraceId(Random.nextLong(), Random.nextLong()),
        spanId = Random.nextLong(),
        parent = parent,
    )
    CurrentSpan.value = span
    for (sink in Trace.activeSinks()) if (sink.flagsFor(site) != 0) sink.onStart(span)
    return span
}

@TraceProbeApi
fun traceThrew(span: Span?, error: Throwable) {
    if (span == null) return
    span.outcome = if (error is CancellationException) Outcome.Cancelled else Outcome.Threw(error)
}

@TraceProbeApi
fun traceExit(span: Span?) {
    if (span == null) return
    span.end()
    CurrentSpan.value = span.parent
    for (sink in Trace.activeSinks()) if (sink.flagsFor(span.site) != 0) sink.onEnd(span)
}

internal expect object CurrentSpan {
    var value: Span?
}
