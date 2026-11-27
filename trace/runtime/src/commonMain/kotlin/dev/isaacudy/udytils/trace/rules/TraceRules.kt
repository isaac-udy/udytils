package dev.isaacudy.udytils.trace.rules

import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.SiteTable
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.SpanKind
import dev.isaacudy.udytils.trace.SpanSink
import dev.isaacudy.udytils.trace.TraceProbeApi
import dev.isaacudy.udytils.trace.TraceSite
import dev.isaacudy.udytils.trace.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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
 * of a call, the constructed instance, or the key of a visit. The lambda's receiver is an [S]: a
 * [RuleScope], or a [CallScope] with the call's arguments and result for an [onCalled] selector.
 */
abstract class Selector<out T, out S : RuleScope> internal constructor() {
    abstract fun matches(site: TraceSite, table: SiteTable): Boolean

    /** The instance that a rule receives for a span whose receiver is [receiver]; [Skip] skips the span. */
    internal open fun instance(receiver: Any?): Any? = receiver

    /** The [SiteFlags] that a site this selector matches requires. */
    internal open val flags: Int get() = SiteFlags.RECORD

    @Suppress("UNCHECKED_CAST")
    internal open fun scope(span: Span): S = RuleScope(span) as S

    internal object Skip
}

/** The calls of one declaration, with arguments of type [A] and a result of type [R]. */
class CallSelector<out T, out A, out R> internal constructor(
    private val declarationId: Long,
    private val type: KClass<*>?,
) : Selector<T, CallScope<A, R>>() {
    override fun matches(site: TraceSite, table: SiteTable) = site.declarationId == declarationId
    override fun instance(receiver: Any?): Any? = receiver.takeIf { type?.isInstance(it) == true }
    override val flags: Int get() = SiteFlags.RECORD or SiteFlags.ARGS

    @Suppress("UNCHECKED_CAST")
    override fun scope(span: Span): CallScope<A, R> =
        CallScope(span, argsOf(span.arguments) as A, span.result.takeIf { span.outcome == Outcome.Returned } as R?)
}

internal class ConstructedSelector(private val declarationId: Long, private val type: KClass<*>) : Selector<Any?, RuleScope>() {
    override fun matches(site: TraceSite, table: SiteTable) = site.declarationId == declarationId

    // A subclass's construction runs this class's initializers first, before the subclass's own properties are set.
    override fun instance(receiver: Any?): Any? = if (receiver != null && receiver::class == type) receiver else Skip
}

internal class VisitSelector(private val name: String) : Selector<Any?, RuleScope>() {
    override fun matches(site: TraceSite, table: SiteTable) = site.isVisit && site.name == name
}

internal class ConstructSelector(private val construct: String) : Selector<Any?, RuleScope>() {
    override fun matches(site: TraceSite, table: SiteTable) = construct in table.constructsOf(site.siteId)
}

/** The compiler plugin replaces each `onCalled` call with a call of this function. */
@TraceProbeApi
@Suppress("UNCHECKED_CAST")
fun <S> calledSelector(declarationId: Long, type: KClass<*>?): S = CallSelector<Any?, Any?, Any?>(declarationId, type) as S

/** The compiler plugin replaces each `onConstructed` call with a call of this function. */
@TraceProbeApi
@Suppress("UNCHECKED_CAST")
fun <T : Any> constructedSelector(declarationId: Long, type: KClass<T>): Selector<T, RuleScope> = ConstructedSelector(declarationId, type) as Selector<T, RuleScope>

private fun requiresPlugin(name: String): Nothing = error("$name requires the dev.isaacudy.udytils.trace compiler plugin")

/**
 * The calls of the referenced function: of the declaration and of every override and `fun interface`
 * lambda of it. The instance is the call's receiver; it is null for a `fun interface` lambda, which
 * has no receiver. The rule's [CallScope] holds the call's arguments, as [Args1] to [Args4], and its result.
 * The reference names a member or extension function, such as `Greet::invoke`.
 */
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled0") fun <R> onCalled(reference: KFunction0<R>): CallSelector<Nothing?, Args0, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled1") fun <T, R> onCalled(reference: KFunction1<T, R>): CallSelector<T?, Args0, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled2") fun <T, A, R> onCalled(reference: KFunction2<T, A, R>): CallSelector<T?, Args1<A>, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled3") fun <T, A, B, R> onCalled(reference: KFunction3<T, A, B, R>): CallSelector<T?, Args2<A, B>, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled4") fun <T, A, B, C, R> onCalled(reference: KFunction4<T, A, B, C, R>): CallSelector<T?, Args3<A, B, C>, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalled5") fun <T, A, B, C, D, R> onCalled(reference: KFunction5<T, A, B, C, D, R>): CallSelector<T?, Args4<A, B, C, D>, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend0") fun <R> onCalled(reference: KSuspendFunction0<R>): CallSelector<Nothing?, Args0, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend1") fun <T, R> onCalled(reference: KSuspendFunction1<T, R>): CallSelector<T?, Args0, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend2") fun <T, A, R> onCalled(reference: KSuspendFunction2<T, A, R>): CallSelector<T?, Args1<A>, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend3") fun <T, A, B, R> onCalled(reference: KSuspendFunction3<T, A, B, R>): CallSelector<T?, Args2<A, B>, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend4") fun <T, A, B, C, R> onCalled(reference: KSuspendFunction4<T, A, B, C, R>): CallSelector<T?, Args3<A, B, C>, R> = requiresPlugin("onCalled")
@Suppress("UNUSED_PARAMETER") @JvmName("onCalledSuspend5") fun <T, A, B, C, D, R> onCalled(reference: KSuspendFunction5<T, A, B, C, D, R>): CallSelector<T?, Args4<A, B, C, D>, R> = requiresPlugin("onCalled")

/**
 * The constructions of [type]: its property initializers and `init` blocks, for an instance whose
 * class is exactly [type]. The instance is the constructed instance.
 */
@Suppress("UNUSED_PARAMETER")
fun <T : Any> onConstructed(type: KClass<T>): Selector<T, RuleScope> = requiresPlugin("onConstructed")

/** The calls of the sites whose declaration belongs to an architecture Construct, such as `ClientData.Repository`. */
fun construct(construct: String): Selector<Any?, RuleScope> = ConstructSelector(construct)

/** The [SpanKind.Visit] spans named [name], which [dev.isaacudy.udytils.trace.Trace.startVisit] opens. */
fun visit(name: String): Selector<Any?, RuleScope> = VisitSelector(name)

/**
 * A [Selector] whose rules suspend in the [CoroutineScope] that [scope] returns for each instance,
 * such as `{ it.viewModelScope }`. The rule starts when the span ends and stops, recording nothing,
 * when that scope is cancelled. A span whose instance is null or not a [T] starts no rule.
 */
class ScopedSelector<T : Any, out S : RuleScope> internal constructor(
    internal val selector: Selector<T?, S>,
    internal val scope: (T) -> CoroutineScope,
)

fun <T : Any, S : RuleScope> Selector<T?, S>.scoped(scope: (T) -> CoroutineScope): ScopedSelector<T, S> = ScopedSelector(this, scope)

enum class Trigger { Start, End }

/** The context of one rule evaluation: the [span] that the rule applies to. */
open class RuleScope internal constructor(val span: Span)

/**
 * The context of a rule on an [onCalled] selector: the call's [args] and, when the call returned,
 * its [result]. [result] is null at [Trigger.Start] and when the call threw or was cancelled.
 * Rules read both; no exporter sends them.
 */
class CallScope<out A, out R> internal constructor(span: Span, val args: A, val result: R?) : RuleScope(span)

/** The arguments of a call without parameters. */
data object Args0

data class Args1<out A>(val first: A)

data class Args2<out A, out B>(val first: A, val second: B)

data class Args3<out A, out B, out C>(val first: A, val second: B, val third: C)

data class Args4<out A, out B, out C, out D>(val first: A, val second: B, val third: C, val fourth: D)

internal fun argsOf(values: Array<Any?>?): Any = when (values?.size ?: 0) {
    0 -> Args0
    1 -> Args1(values!![0])
    2 -> Args2(values!![0], values[1])
    3 -> Args3(values!![0], values[1], values[2])
    else -> Args4(values!![0], values[1], values[2], values[3])
}

/** Receives the analytics events that [AnalyticsRules] build. */
fun interface AnalyticsSink<E> {
    fun record(event: E, context: Map<String, String>)
}

/** Receives the timings that [MetricRules] build: each [metric] with its duration. */
fun interface MetricsSink<M> {
    fun recordTiming(metric: M, durationNanos: Long)
}

internal class Rule<R>(
    val selector: Selector<*, *>,
    val kinds: Set<SpanKind>,
    val trigger: Trigger,
    val build: RuleScope.(Any?) -> R?,
    val scope: (Span) -> RuleScope = selector::scope,
)

internal class ScopedRule<R>(
    val selector: ScopedSelector<*, *>,
    val kinds: Set<SpanKind>,
    val build: suspend RuleScope.(Any) -> R?,
)

private val CALLS = setOf(SpanKind.Call, SpanKind.Construction, SpanKind.Visit)

/**
 * Delivers each item to [deliver] in order, in one coroutine of [scope], so a sink that blocks
 * never blocks the thread that ended the span. An exception from [deliver] drops that item.
 */
private class Delivery<T>(scope: CoroutineScope, deliver: (T) -> Unit) {
    private val queue = Channel<T>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (item in queue) {
                try {
                    deliver(item)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    continue
                }
            }
        }
    }

    fun send(item: T) {
        queue.trySend(item)
    }
}

/**
 * Applies [rules] and [scopedRules] to the spans of the sites of [table]. A rule runs on the thread
 * that starts or ends the span, so it reads the instance's state at that moment; [record] receives
 * each result with the span's duration: to the span's end for a rule, to the rule's return for a
 * scoped rule.
 */
private fun <R> ruleSink(
    table: SiteTable,
    rules: List<Rule<R>>,
    scopedRules: List<ScopedRule<R>>,
    record: (R, Long) -> Unit,
): SpanSink = object : SpanSink {
    override fun flagsFor(site: TraceSite): Int {
        var flags = 0
        for (rule in rules) if (rule.selector.matches(site, table)) flags = flags or rule.selector.flags
        for (rule in scopedRules) if (rule.selector.selector.matches(site, table)) flags = flags or rule.selector.selector.flags
        return flags
    }

    override fun onStart(span: Span) = apply(span, Trigger.Start)

    override fun onEnd(span: Span) {
        apply(span, Trigger.End)
        for (rule in scopedRules) {
            val selector = rule.selector.selector
            if (span.kind !in rule.kinds || !selector.matches(span.site, table)) continue
            val instance = selector.instance(span.receiver) ?: continue
            if (instance === Selector.Skip) continue
            launch(rule, selector.scope(span), instance)
        }
    }

    private fun apply(span: Span, trigger: Trigger) {
        for (rule in rules) {
            if (rule.trigger != trigger || span.kind !in rule.kinds || !rule.selector.matches(span.site, table)) continue
            val instance = rule.selector.instance(span.receiver)
            if (instance === Selector.Skip) continue
            val result = try {
                rule.scope(span).(rule.build)(instance)
            } catch (error: Exception) {
                null
            } ?: continue
            record(result, span.durationNanos)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun launch(rule: ScopedRule<R>, ruleScope: RuleScope, instance: Any) {
        val selector = rule.selector as ScopedSelector<Any, *>
        val scope = try {
            selector.scope(instance)
        } catch (error: Exception) {
            return
        }
        scope.launch {
            val result = try {
                ruleScope.(rule.build)(instance)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            } ?: return@launch
            record(result, ruleScope.span.startMark.elapsedNow().inWholeNanoseconds)
        }
    }
}

/** The analytics rules of one application: which spans become analytics events. */
class AnalyticsRules<E : Any> internal constructor(
    private val context: () -> Map<String, String>,
    private val rules: List<Rule<E>>,
    private val scopedRules: List<ScopedRule<E>>,
) {
    /**
     * A [SpanSink] that applies the rules to the sites of [table] and writes to [analytics] in a
     * coroutine of [delivery], such as `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
     * Cancelling [delivery] stops the writes.
     */
    fun sink(table: SiteTable, analytics: AnalyticsSink<E>, delivery: CoroutineScope): SpanSink {
        val queue = Delivery<Pair<E, Map<String, String>>>(delivery) { (event, context) -> analytics.record(event, context) }
        return ruleSink(table, rules, scopedRules) { event, _ -> queue.send(event to context()) }
    }
}

/** The metric rules of one application: which spans become timings. */
class MetricRules<M : Any> internal constructor(
    private val rules: List<Rule<M>>,
    private val scopedRules: List<ScopedRule<M>>,
) {
    /** A [SpanSink] that applies the rules to the sites of [table] and writes to [metrics] in a coroutine of [delivery]. */
    fun sink(table: SiteTable, metrics: MetricsSink<M>, delivery: CoroutineScope): SpanSink {
        val queue = Delivery<Pair<M, Long>>(delivery) { (metric, durationNanos) -> metrics.recordTiming(metric, durationNanos) }
        return ruleSink(table, rules, scopedRules) { metric, durationNanos -> queue.send(metric to durationNanos) }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <S, T, R> erase(build: S.(T) -> R?): RuleScope.(Any?) -> R? = build as RuleScope.(Any?) -> R?

@Suppress("UNCHECKED_CAST")
private fun <S, T, R> erase(build: suspend S.(T) -> R?): suspend RuleScope.(Any) -> R? = build as suspend RuleScope.(Any) -> R?

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
    fun <T, S : RuleScope> Selector<T, S>.event(trigger: Trigger = Trigger.End, build: S.(T) -> E?) {
        rules += Rule(this, CALLS, trigger, erase(build))
    }

    /** An analytics event when [build] returns non-null, after the span ends, in the instance's scope. */
    fun <T : Any, S : RuleScope> ScopedSelector<T, S>.event(build: suspend S.(T) -> E?) {
        scopedRules += ScopedRule(this, CALLS, erase(build))
    }

    internal fun build() = AnalyticsRules(context, rules.toList(), scopedRules.toList())
}

class MetricRulesBuilder<M : Any> internal constructor() {
    private val rules = mutableListOf<Rule<M>>()
    private val scopedRules = mutableListOf<ScopedRule<M>>()

    /** The duration of each call or construction that this selector selects; [build] returns the metric, or null to record none. */
    fun <T, S : RuleScope> Selector<T, S>.timing(build: S.(T) -> M?) {
        rules += Rule(this, setOf(SpanKind.Call, SpanKind.Construction), Trigger.End, erase(build))
    }

    /**
     * The time from the start of each collection of the Flow that this selector's functions return
     * to the collection's first value. The instance is null.
     */
    fun <T> Selector<T, *>.firstValue(build: RuleScope.(T?) -> M?) {
        rules += Rule(this, setOf(SpanKind.FirstValue), Trigger.End, erase(build)) { RuleScope(it) }
    }

    /**
     * The time from the start of each call, construction or visit that this selector selects until
     * [build] returns non-null, in the instance's scope. For example, until a ViewModel's state has loaded.
     */
    fun <T : Any, S : RuleScope> ScopedSelector<T, S>.timing(build: suspend S.(T) -> M?) {
        scopedRules += ScopedRule(this, CALLS, erase(build))
    }

    internal fun build() = MetricRules(rules.toList(), scopedRules.toList())
}

fun <E : Any> analyticsRules(block: AnalyticsRulesBuilder<E>.() -> Unit): AnalyticsRules<E> = AnalyticsRulesBuilder<E>().apply(block).build()

fun <M : Any> metricRules(block: MetricRulesBuilder<M>.() -> Unit): MetricRules<M> = MetricRulesBuilder<M>().apply(block).build()
