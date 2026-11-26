package dev.isaacudy.udytils.trace.rules

import dev.isaacudy.udytils.trace.Outcome
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

/** Receives the analytics events that the rules build. */
fun interface AnalyticsSink<E> {
    fun record(event: E, context: Map<String, String>)
}

/** Receives the timings that the rules measure. */
fun interface MetricsSink {
    fun recordTiming(name: String, durationNanos: Long, tags: Map<String, String>)
}

/** The measurement rules of one application: which spans become analytics events and timings. */
class TraceRules<E : Any> internal constructor(
    private val context: () -> Map<String, String>,
    private val events: List<EventRule<E>>,
    private val timings: List<TimingRule>,
) {
    /** A [SpanSink] that applies the rules to the sites of [table] and writes to [analytics] and [metrics]. */
    fun sink(table: SiteTable, analytics: AnalyticsSink<E>, metrics: MetricsSink): SpanSink = object : SpanSink {
        override fun flagsFor(site: TraceSite): Int =
            if (events.any { it.selector.matches(site, table) } || timings.any { it.selector.matches(site, table) }) SiteFlags.RECORD else 0

        override fun onStart(span: Span) = emit(span, Trigger.Start)

        override fun onEnd(span: Span) {
            emit(span, Trigger.End)
            for (rule in timings) {
                if (rule.kind != span.kind || !rule.selector.matches(span.site, table)) continue
                metrics.recordTiming(rule.name ?: span.site.name, span.durationNanos, mapOf("kind" to span.kind.name, "outcome" to span.outcome.tag()))
            }
        }

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

internal class EventRule<E>(val selector: Selector, val trigger: Trigger, val build: (Span) -> E?)

internal class TimingRule(val selector: Selector, val kind: SpanKind, val name: String?)

class TraceRulesBuilder<E : Any> internal constructor() {
    private var context: () -> Map<String, String> = { emptyMap() }
    private val events = mutableListOf<EventRule<E>>()
    private val timings = mutableListOf<TimingRule>()

    /** Properties that every analytics event of these rules carries, such as a session ID. */
    fun context(provide: () -> Map<String, String>) {
        context = provide
    }

    /**
     * An analytics event for each call or visit of the [selector]'s sites. A [Trigger.Start] event is built
     * when the call starts, so the event never depends on the call ending; [build] returns null to
     * record no event for a span.
     */
    fun event(selector: Selector, trigger: Trigger = Trigger.End, build: (Span) -> E?) {
        events += EventRule(selector, trigger, build)
    }

    /** The duration of each call of the [selector]'s sites, tagged with the call's outcome. */
    fun timing(selector: Selector, name: String? = null) {
        timings += TimingRule(selector, SpanKind.Call, name)
    }

    /** The time from the start of each collection of the [selector]'s Flow to the collection's first value. */
    fun firstValue(selector: Selector, name: String? = null) {
        timings += TimingRule(selector, SpanKind.FirstValue, name)
    }

    internal fun build() = TraceRules(context, events.toList(), timings.toList())
}

fun <E : Any> traceRules(block: TraceRulesBuilder<E>.() -> Unit): TraceRules<E> = TraceRulesBuilder<E>().apply(block).build()

private fun Outcome.tag(): String = when (this) {
    Outcome.Running -> "running"
    Outcome.Returned -> "returned"
    Outcome.Cancelled -> "cancelled"
    is Outcome.Threw -> "threw"
}
