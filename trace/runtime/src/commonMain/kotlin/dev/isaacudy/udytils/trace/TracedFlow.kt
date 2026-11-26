package dev.isaacudy.udytils.trace

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

/** Returns [upstream] wrapped so each collection records a [SpanKind.Collect] span for [site]. */
@TraceProbeApi
fun <T> traceFlow(site: TraceSite, upstream: Flow<T>): Flow<T> = TracedFlow(site, upstream)

/**
 * A collection span is current while the upstream runs, so instrumented upstream code nests
 * under it; the collector's own span is current while the downstream collector runs.
 */
private class TracedFlow<T>(private val site: TraceSite, private val upstream: Flow<T>) : Flow<T> {
    @OptIn(TraceProbeApi::class)
    override suspend fun collect(collector: FlowCollector<T>) {
        if (site.flags == 0) return upstream.collect(collector)
        val context = currentCoroutineContext()
        val parent = traceAdopt(context, site)
        val span = if (site.flags and SiteFlags.RECORD == 0) null else {
            newSpan(site, parent, previous = null, reported = true, kind = SpanKind.Collect).also(::notifyStart)
        }
        var emissions = 0
        try {
            traceSuspend(span, parent, context)
            upstream.collect { value ->
                emissions++
                if (emissions == 1 && span != null) recordFirstValue(span)
                traceSuspend(null, parent, context)
                collector.emit(value)
                traceSuspend(span, parent, context)
            }
        } catch (error: Throwable) {
            traceThrew(span, error)
            throw error
        } finally {
            span?.record(Span.EMISSIONS, emissions)
            traceEndSuspend(span, parent, context)
        }
    }

    private fun recordFirstValue(collection: Span) {
        val firstValue = newSpan(site, collection, previous = null, reported = true, kind = SpanKind.FirstValue, start = collection.startMark)
        firstValue.end()
        notifyStart(firstValue)
        notifyEnd(firstValue)
    }
}
