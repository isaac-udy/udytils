@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.isaacudy.udytils.trace

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** The span that a coroutine started with this element adopts as its parent. */
class TraceContext(val span: Span) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TraceContext>
}

/**
 * Opens a span for a non-suspend [site]. Returns null when the site is untraced, so the call
 * leaves the current span unchanged and its callees attach to the caller's span. [receiver] is the
 * dispatch receiver of the traced function, or null.
 */
@TraceProbeApi
fun traceEnter(site: TraceSite, receiver: Any?): Span? {
    val flags = site.flags
    val root = flags and SiteFlags.ROOT != 0
    val record = flags and SiteFlags.RECORD != 0
    if (!root && !record) return null
    val previous = CurrentSpan.value
    val span = newSpan(site, parent = if (root) null else previous?.takeIf { it.reported }, previous = previous, reported = record)
    if (record && span.parent == null) span.links = Trace.linksFor(receiver)
    CurrentSpan.value = span
    if (record) notifyStart(span)
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
    CurrentSpan.value = span.previous
    if (span.reported) notifyEnd(span)
}

/**
 * Returns the parent span for a suspend body that starts in [context]: the handoff from an
 * instrumented caller in the same coroutine or an ancestor coroutine, then the [TraceContext]
 * of [context]. A root [site] consumes the handoff and returns only a remote parent, so a request
 * handler continues the trace of the client that called it.
 */
@TraceProbeApi
fun traceAdopt(context: CoroutineContext, site: TraceSite?): Span? {
    val handoff = CurrentSpan.handoff
    val parent = if (handoff != null && isSelfOrAncestor(CurrentSpan.handoffJob, context[Job])) {
        CurrentSpan.handoff = null
        CurrentSpan.handoffJob = null
        handoff
    } else {
        context[TraceContext]?.span
    }
    if (site != null && site.flags and SiteFlags.ROOT != 0) return parent?.takeIf { it.isRemote }
    return parent
}

/**
 * Opens a span for a suspend [site] under [parent]; returns null when no sink records the site.
 * [receiver] is the dispatch receiver of the traced function, or null.
 */
@TraceProbeApi
fun traceEnterSuspend(site: TraceSite, parent: Span?, receiver: Any?): Span? {
    if (site.flags and SiteFlags.RECORD == 0) return null
    val span = newSpan(site, parent, previous = null, reported = true)
    if (parent == null) span.links = Trace.linksFor(receiver)
    return span.also(::notifyStart)
}

/** Makes the body's span current; the plugin calls it at body entry and after each suspend call returns. */
@TraceProbeApi
fun traceResume(span: Span?, parent: Span?) {
    val current = span ?: parent
    CurrentSpan.value = current
    if (current != null && CurrentSpan.handoff === current) {
        CurrentSpan.handoff = null
        CurrentSpan.handoffJob = null
    }
}

/**
 * Hands the body's span to the suspend callee and clears the current span; the plugin calls it
 * immediately before each suspend call, so a suspended coroutine leaves no current span behind.
 */
@TraceProbeApi
fun traceSuspend(span: Span?, parent: Span?, context: CoroutineContext) {
    CurrentSpan.handoff = span ?: parent
    CurrentSpan.handoffJob = context[Job]
    CurrentSpan.value = null
}

/**
 * Ends the body's span and hands [parent] back, so an uninstrumented caller that calls further
 * instrumented code, such as the next operator of a Flow, keeps the caller's span.
 */
@TraceProbeApi
fun traceEndSuspend(span: Span?, parent: Span?, context: CoroutineContext) {
    CurrentSpan.value = null
    CurrentSpan.handoff = parent
    CurrentSpan.handoffJob = context[Job]
    if (span == null) return
    span.end()
    notifyEnd(span)
}

/** Adds the current span to the context of a coroutine that the call launches. */
@TraceProbeApi
fun traceContext(existing: CoroutineContext?): CoroutineContext {
    val base = existing ?: kotlin.coroutines.EmptyCoroutineContext
    val span = CurrentSpan.value?.takeIf { it.reported } ?: return base
    return base + TraceContext(span)
}

/** The plugin replaces each call with the declaration ID of [reference]'s declaration. */
@Suppress("UNUSED_PARAMETER")
fun declarationIdOf(reference: kotlin.reflect.KCallable<*>): Long =
    error("declarationIdOf requires the dev.isaacudy.udytils.trace compiler plugin")

/** The handoff that a suspend body starting in [context] would adopt, without consuming it. */
internal fun handoffFor(context: CoroutineContext): Span? {
    val handoff = CurrentSpan.handoff ?: return null
    return handoff.takeIf { it.reported && isSelfOrAncestor(CurrentSpan.handoffJob, context[Job]) }
}

private fun isSelfOrAncestor(candidate: Job?, job: Job?): Boolean {
    if (candidate == null) return job == null
    var current = job
    while (current != null) {
        if (current === candidate) return true
        current = current.parent
    }
    return false
}

internal fun newSpan(
    site: TraceSite,
    parent: Span?,
    previous: Span?,
    reported: Boolean,
    kind: SpanKind = SpanKind.Call,
    start: kotlin.time.TimeSource.Monotonic.ValueTimeMark = kotlin.time.TimeSource.Monotonic.markNow(),
) = Span(
    site = site,
    traceId = parent?.traceId ?: TraceId(Random.nextLong(), Random.nextLong()),
    spanId = Random.nextLong(),
    parent = parent,
    previous = previous,
    reported = reported,
    kind = kind,
    start = start,
)

internal fun notifyStart(span: Span) {
    for (sink in Trace.activeSinks()) if (sink.flagsFor(span.site) != 0) sink.onStart(span)
}

internal fun notifyEnd(span: Span) {
    for (sink in Trace.activeSinks()) if (sink.flagsFor(span.site) != 0) sink.onEnd(span)
}

internal expect object CurrentSpan {
    var value: Span?
    var handoff: Span?
    var handoffJob: Job?
}
