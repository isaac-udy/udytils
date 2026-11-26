package dev.isaacudy.udytils.trace.rules

import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.SiteTable
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.SpanKind
import dev.isaacudy.udytils.trace.SpanSink
import dev.isaacudy.udytils.trace.TraceSite
import kotlin.reflect.KCallable

/** The sites a rule applies to. */
sealed interface Selector {
    fun matches(site: TraceSite, table: SiteTable): Boolean

    /** The sites of one declaration and every override and `fun interface` lambda of it. */
    class Declaration(val declarationId: Long) : Selector {
        override fun matches(site: TraceSite, table: SiteTable) = site.declarationId == declarationId
    }

    /** The [SpanKind.Visit] spans named [name], which [dev.isaacudy.udytils.trace.Trace.startVisit] opens. */
    class Visit(val name: String) : Selector {
        override fun matches(site: TraceSite, table: SiteTable) = site.isVisit && site.name == name
    }

    /** The sites whose declaration belongs to an architecture Construct, such as `ClientData.Repository`. */
    class Construct(val construct: String) : Selector {
        override fun matches(site: TraceSite, table: SiteTable) = construct in table.constructsOf(site.siteId)
    }
}

/** The plugin replaces each call with a [Selector.Declaration] of [reference]'s declaration. */
@Suppress("UNUSED_PARAMETER")
fun on(reference: KCallable<*>): Selector =
    error("on requires the dev.isaacudy.udytils.trace compiler plugin")

fun construct(construct: String): Selector = Selector.Construct(construct)

fun visit(name: String): Selector = Selector.Visit(name)

enum class Trigger { Start, End }

/** Receives the analytics events that [AnalyticsRules] build. */
fun interface AnalyticsSink<E> {
    fun record(event: E, context: Map<String, String>)
}

/** Receives the timings that [MetricRules] build: each [metric] with its duration. */
fun interface MetricsSink<M> {
    fun recordTiming(metric: M, durationNanos: Long)
}

/** The analytics rules of one application: which spans become analytics events. */
class AnalyticsRules<E : Any> internal constructor(
    private val context: () -> Map<String, String>,
    private val events: List<EventRule<E>>,
) {
    /** A [SpanSink] that applies the rules to the sites of [table] and writes to [analytics]. */
    fun sink(table: SiteTable, analytics: AnalyticsSink<E>): SpanSink = object : SpanSink {
        override fun flagsFor(site: TraceSite): Int =
            if (events.any { it.selector.matches(site, table) }) SiteFlags.RECORD else 0

        override fun onStart(span: Span) = emit(span, Trigger.Start)

        override fun onEnd(span: Span) = emit(span, Trigger.End)

        private fun emit(span: Span, trigger: Trigger) {
            if (span.kind != SpanKind.Call && span.kind != SpanKind.Visit) return
            for (rule in events) {
                if (rule.trigger != trigger || !rule.selector.matches(span.site, table)) continue
                val event = rule.build(span) ?: continue
                analytics.record(event, context())
            }
        }
    }
}

/** The metric rules of one application: which spans become timings. */
class MetricRules<M : Any> internal constructor(private val timings: List<TimingRule<M>>) {
    /** A [SpanSink] that applies the rules to the sites of [table] and writes to [metrics]. */
    fun sink(table: SiteTable, metrics: MetricsSink<M>): SpanSink = object : SpanSink {
        override fun flagsFor(site: TraceSite): Int =
            if (timings.any { it.selector.matches(site, table) }) SiteFlags.RECORD else 0

        override fun onEnd(span: Span) {
            for (rule in timings) {
                if (rule.kind != span.kind || !rule.selector.matches(span.site, table)) continue
                val metric = rule.build(span) ?: continue
                metrics.recordTiming(metric, span.durationNanos)
            }
        }
    }
}

internal class EventRule<E>(val selector: Selector, val trigger: Trigger, val build: (Span) -> E?)

internal class TimingRule<M>(val selector: Selector, val kind: SpanKind, val build: (Span) -> M?)

class AnalyticsRulesBuilder<E : Any> internal constructor() {
    private var context: () -> Map<String, String> = { emptyMap() }
    private val events = mutableListOf<EventRule<E>>()

    /** Properties that every analytics event of these rules carries, such as a session ID. */
    fun context(provide: () -> Map<String, String>) {
        context = provide
    }

    /**
     * An analytics event for each call or visit of the [selector]'s sites. A [Trigger.Start] event
     * is built when the call starts, so the event never depends on the call ending; [build]
     * returns null to record no event for a span.
     */
    fun event(selector: Selector, trigger: Trigger = Trigger.End, build: (Span) -> E?) {
        events += EventRule(selector, trigger, build)
    }

    internal fun build() = AnalyticsRules(context, events.toList())
}

class MetricRulesBuilder<M : Any> internal constructor() {
    private val timings = mutableListOf<TimingRule<M>>()

    /**
     * A timing for each call of the [selector]'s sites, from the call's start to its end. [build]
     * returns the metric to record, for example by the call's `outcome`, or null to record none.
     */
    fun timing(selector: Selector, build: (Span) -> M?) {
        timings += TimingRule(selector, SpanKind.Call, build)
    }

    /**
     * A timing for each collection of the Flow that the [selector]'s sites return, from the start
     * of the collection to the collection's first value. [build] returns the metric to record, or null.
     */
    fun firstValue(selector: Selector, build: (Span) -> M?) {
        timings += TimingRule(selector, SpanKind.FirstValue, build)
    }

    internal fun build() = MetricRules(timings.toList())
}

fun <E : Any> analyticsRules(block: AnalyticsRulesBuilder<E>.() -> Unit): AnalyticsRules<E> = AnalyticsRulesBuilder<E>().apply(block).build()

fun <M : Any> metricRules(block: MetricRulesBuilder<M>.() -> Unit): MetricRules<M> = MetricRulesBuilder<M>().apply(block).build()
