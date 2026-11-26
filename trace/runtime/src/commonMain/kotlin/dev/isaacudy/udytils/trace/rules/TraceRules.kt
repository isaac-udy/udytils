package dev.isaacudy.udytils.trace.rules

import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.SiteTable
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.SpanKind
import dev.isaacudy.udytils.trace.SpanSink
import dev.isaacudy.udytils.trace.TraceProbeApi
import dev.isaacudy.udytils.trace.TraceSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.reflect.KFunction0
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2
import kotlin.reflect.KFunction3
import kotlin.reflect.KFunction4
import kotlin.reflect.KFunction5
import kotlin.reflect.KSuspendFunction0
import kotlin.reflect.KSuspendFunction1
import kotlin.reflect.KSuspendFunction2
import kotlin.reflect.KSuspendFunction3
import kotlin.reflect.KSuspendFunction4
import kotlin.reflect.KSuspendFunction5

/**
 * The spans a rule applies to. A rule's lambda receives the span's instance as a [T]: the receiver
 * of a call, the constructed instance, or the key of a visit.
 */
abstract class Selector<out T> internal constructor() {
    abstract fun matches(site: TraceSite, table: SiteTable): Boolean

    /** The instance that a rule receives for a span whose receiver is [receiver]; [Skip] skips the span. */
    internal open fun instance(receiver: Any?): Any? = receiver

    internal object Skip
}

internal class DeclarationSelector(private val declarationId: Long, private val type: KClass<*>?) : Selector<Any?>() {
    override fun matches(site: TraceSite, table: SiteTable) = site.declarationId == declarationId
    override fun instance(receiver: Any?): Any? = receiver.takeIf { type?.isInstance(it) == true }
}

internal class ConstructedSelector(private val declarationId: Long, private val type: KClass<*>) : Selector<Any?>() {
    override fun matches(site: TraceSite, table: SiteTable) = site.declarationId == declarationId

    // A subclass's construction runs this class's initializers first, before the subclass's own properties are set.
    override fun instance(receiver: Any?): Any? = if (receiver != null && receiver::class == type) receiver else Skip
}

internal class VisitSelector(private val name: String) : Selector<Any?>() {
    override fun matches(site: TraceSite, table: SiteTable) = site.isVisit && site.name == name
}

internal class ConstructSelector(private val construct: String) : Selector<Any?>() {
    override fun matches(site: TraceSite, table: SiteTable) = construct in table.constructsOf(site.siteId)
}

/** The compiler plugin replaces each `onCalled` call with a call of this function. */
@TraceProbeApi
@Suppress("UNCHECKED_CAST")
fun <T> calledSelector(declarationId: Long, type: KClass<*>?): Selector<T?> = DeclarationSelector(declarationId, type) as Selector<T?>

/** The compiler plugin replaces each `onConstructed` call with a call of this function. */
@TraceProbeApi
@Suppress("UNCHECKED_CAST")
fun <T : Any> constructedSelector(declarationId: Long, type: KClass<T>): Selector<T> = ConstructedSelector(declarationId, type) as Selector<T>

private fun requiresPlugin(name: String): Nothing = error("$name requires the dev.isaacudy.udytils.trace compiler plugin")

/**
 * The calls of the referenced function: of the declaration and of every override and `fun interface`
 * lambda of it. The instance is the call's receiver; it is null for a top-level function and for a
 * `fun interface` lambda, which has no receiver.
 */
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled0") fun onCalled(reference: KFunction0<*>): Selector<Nothing?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled1") fun <T> onCalled(reference: KFunction1<T, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled2") fun <T> onCalled(reference: KFunction2<T, *, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled3") fun <T> onCalled(reference: KFunction3<T, *, *, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled4") fun <T> onCalled(reference: KFunction4<T, *, *, *, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled5") fun <T> onCalled(reference: KFunction5<T, *, *, *, *, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend0") fun onCalled(reference: KSuspendFunction0<*>): Selector<Nothing?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend1") fun <T> onCalled(reference: KSuspendFunction1<T, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend2") fun <T> onCalled(reference: KSuspendFunction2<T, *, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend3") fun <T> onCalled(reference: KSuspendFunction3<T, *, *, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend4") fun <T> onCalled(reference: KSuspendFunction4<T, *, *, *, *>): Selector<T?> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend5") fun <T> onCalled(reference: KSuspendFunction5<T, *, *, *, *, *>): Selector<T?> = requiresPlugin("onCalled")

/**
 * The constructions of [type]: its property initializers and `init` blocks, for an instance whose
 * class is exactly [type]. The instance is the constructed instance.
 */
@Suppress("UNUSED_PARAMETER")
fun <T : Any> onConstructed(type: KClass<T>): Selector<T> = requiresPlugin("onConstructed")

/** The calls of the sites whose declaration belongs to an architecture Construct, such as `ClientData.Repository`. */
fun construct(construct: String): Selector<Any?> = ConstructSelector(construct)

/** The [SpanKind.Visit] spans named [name], which [dev.isaacudy.udytils.trace.Trace.startVisit] opens. */
fun visit(name: String): Selector<Any?> = VisitSelector(name)

/**
 * A [Selector] whose rules suspend in the [CoroutineScope] that [scope] returns for each instance,
 * such as `{ it.viewModelScope }`. The rule starts when the span ends and stops, recording nothing,
 * when that scope is cancelled. A span whose instance is null or not a [T] starts no rule.
 */
class ScopedSelector<T : Any> internal constructor(
    internal val selector: Selector<T?>,
    internal val scope: (T) -> CoroutineScope,
)

fun <T : Any> Selector<T?>.scoped(scope: (T) -> CoroutineScope): ScopedSelector<T> = ScopedSelector(this, scope)

enum class Trigger { Start, End }

/** The context of one rule evaluation: the [span] that the rule applies to. */
class RuleScope internal constructor(val span: Span)

/** Receives the analytics events that [AnalyticsRules] build. */
fun interface AnalyticsSink<E> {
    fun record(event: E, context: Map<String, String>)
}

/** Receives the timings that [MetricRules] build: each [metric] with its duration. */
fun interface MetricsSink<M> {
    fun recordTiming(metric: M, durationNanos: Long)
}

internal class Rule<R>(
    val selector: Selector<*>,
    val kinds: Set<SpanKind>,
    val trigger: Trigger,
    val build: RuleScope.(Any?) -> R?,
)

internal class ScopedRule<R>(
    val selector: ScopedSelector<*>,
    val kinds: Set<SpanKind>,
    val build: suspend RuleScope.(Any) -> R?,
)

private val CALLS = setOf(SpanKind.Call, SpanKind.Construction, SpanKind.Visit)

/**
 * Applies [rules] and [scopedRules] to the spans of the sites of [table]. [record] receives each
 * result with the span's duration: to the span's end for a rule, to the rule's return for a scoped rule.
 */
private fun <R> ruleSink(
    table: SiteTable,
    rules: List<Rule<R>>,
    scopedRules: List<ScopedRule<R>>,
    record: (R, Long) -> Unit,
): SpanSink = object : SpanSink {
    override fun flagsFor(site: TraceSite): Int {
        val selected = rules.any { it.selector.matches(site, table) } || scopedRules.any { it.selector.selector.matches(site, table) }
        return if (selected) SiteFlags.RECORD else 0
    }

    override fun onStart(span: Span) = apply(span, Trigger.Start)

    override fun onEnd(span: Span) {
        apply(span, Trigger.End)
        for (rule in scopedRules) {
            if (span.kind !in rule.kinds || !rule.selector.selector.matches(span.site, table)) continue
            val instance = rule.selector.selector.instance(span.receiver) ?: continue
            if (instance === Selector.Skip) continue
            launch(rule, span, instance)
        }
    }

    private fun apply(span: Span, trigger: Trigger) {
        for (rule in rules) {
            if (rule.trigger != trigger || span.kind !in rule.kinds || !rule.selector.matches(span.site, table)) continue
            val instance = rule.selector.instance(span.receiver)
            if (instance === Selector.Skip) continue
            val result = try {
                RuleScope(span).(rule.build)(instance)
            } catch (error: Exception) {
                null
            } ?: continue
            record(result, span.durationNanos)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun launch(rule: ScopedRule<R>, span: Span, instance: Any) {
        val selector = rule.selector as ScopedSelector<Any>
        val scope = try {
            selector.scope(instance)
        } catch (error: Exception) {
            return
        }
        scope.launch {
            val result = try {
                RuleScope(span).(rule.build)(instance)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            } ?: return@launch
            record(result, span.startMark.elapsedNow().inWholeNanoseconds)
        }
    }
}

/** The analytics rules of one application: which spans become analytics events. */
class AnalyticsRules<E : Any> internal constructor(
    private val context: () -> Map<String, String>,
    private val rules: List<Rule<E>>,
    private val scopedRules: List<ScopedRule<E>>,
) {
    /** A [SpanSink] that applies the rules to the sites of [table] and writes to [analytics]. */
    fun sink(table: SiteTable, analytics: AnalyticsSink<E>): SpanSink =
        ruleSink(table, rules, scopedRules) { event, _ -> analytics.record(event, context()) }
}

/** The metric rules of one application: which spans become timings. */
class MetricRules<M : Any> internal constructor(
    private val rules: List<Rule<M>>,
    private val scopedRules: List<ScopedRule<M>>,
) {
    /** A [SpanSink] that applies the rules to the sites of [table] and writes to [metrics]. */
    fun sink(table: SiteTable, metrics: MetricsSink<M>): SpanSink =
        ruleSink(table, rules, scopedRules) { metric, durationNanos -> metrics.recordTiming(metric, durationNanos) }
}

@Suppress("UNCHECKED_CAST")
private fun <T, R> erase(build: RuleScope.(T) -> R?): RuleScope.(Any?) -> R? = build as RuleScope.(Any?) -> R?

@Suppress("UNCHECKED_CAST")
private fun <T, R> erase(build: suspend RuleScope.(T) -> R?): suspend RuleScope.(Any) -> R? = build as suspend RuleScope.(Any) -> R?

class AnalyticsRulesBuilder<E : Any> internal constructor() {
    private var context: () -> Map<String, String> = { emptyMap() }
    private val rules = mutableListOf<Rule<E>>()
    private val scopedRules = mutableListOf<ScopedRule<E>>()

    /** Properties that every analytics event of these rules carries, such as a session ID. */
    fun context(provide: () -> Map<String, String>) {
        context = provide
    }

    /**
     * An analytics event for each call, construction or visit that this selector selects. A
     * [Trigger.Start] event is built when the span starts, so the event never depends on the span
     * ending; [build] returns null to record no event. An exception from [build] records no event.
     */
    fun <T> Selector<T>.event(trigger: Trigger = Trigger.End, build: RuleScope.(T) -> E?) {
        rules += Rule(this, CALLS, trigger, erase(build))
    }

    /** An analytics event when [build] returns non-null, after the span ends, in the instance's scope. */
    fun <T : Any> ScopedSelector<T>.event(build: suspend RuleScope.(T) -> E?) {
        scopedRules += ScopedRule(this, CALLS, erase(build))
    }

    internal fun build() = AnalyticsRules(context, rules.toList(), scopedRules.toList())
}

class MetricRulesBuilder<M : Any> internal constructor() {
    private val rules = mutableListOf<Rule<M>>()
    private val scopedRules = mutableListOf<ScopedRule<M>>()

    /** The duration of each call or construction that this selector selects; [build] returns the metric, or null to record none. */
    fun <T> Selector<T>.timing(build: RuleScope.(T) -> M?) {
        rules += Rule(this, setOf(SpanKind.Call, SpanKind.Construction), Trigger.End, erase(build))
    }

    /**
     * The time from the start of each collection of the Flow that this selector's functions return
     * to the collection's first value. The instance is null.
     */
    fun <T> Selector<T>.firstValue(build: RuleScope.(T?) -> M?) {
        rules += Rule(this, setOf(SpanKind.FirstValue), Trigger.End, erase(build))
    }

    /**
     * The time from the start of each call, construction or visit that this selector selects until
     * [build] returns non-null, in the instance's scope. For example, until a ViewModel's state has loaded.
     */
    fun <T : Any> ScopedSelector<T>.timing(build: suspend RuleScope.(T) -> M?) {
        scopedRules += ScopedRule(this, CALLS, erase(build))
    }

    internal fun build() = MetricRules(rules.toList(), scopedRules.toList())
}

fun <E : Any> analyticsRules(block: AnalyticsRulesBuilder<E>.() -> Unit): AnalyticsRules<E> = AnalyticsRulesBuilder<E>().apply(block).build()

fun <M : Any> metricRules(block: MetricRulesBuilder<M>.() -> Unit): MetricRules<M> = MetricRulesBuilder<M>().apply(block).build()
