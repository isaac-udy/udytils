package sample

import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.rules.Trigger
import dev.isaacudy.udytils.trace.rules.analyticsRules
import dev.isaacudy.udytils.trace.rules.metricRules
import dev.isaacudy.udytils.trace.rules.on

sealed interface SampleEvent {
    data class Formatting(val count: Int) : SampleEvent
    data object Formatted : SampleEvent
}

sealed interface SampleMetric {
    data class Format(val succeeded: Boolean) : SampleMetric
    data object GreetingsLoad : SampleMetric
}

fun sampleAnalyticsRules() = analyticsRules<SampleEvent> {
    var formatting = 0
    context { mapOf("session" to "s1") }
    event(on(Formatter::format), Trigger.Start) { SampleEvent.Formatting(++formatting) }
    event(on(Formatter::format)) { SampleEvent.Formatted }
}

fun sampleMetricRules() = metricRules<SampleMetric> {
    timing(on(Formatter::format)) { span -> SampleMetric.Format(succeeded = span.outcome == Outcome.Returned) }
    firstValue(on(FlowOfGreetings::invoke)) { SampleMetric.GreetingsLoad }
}
