package sample

import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.rules.Trigger
import dev.isaacudy.udytils.trace.rules.analyticsRules
import dev.isaacudy.udytils.trace.rules.metricRules
import dev.isaacudy.udytils.trace.rules.onCalled
import dev.isaacudy.udytils.trace.rules.onConstructed
import dev.isaacudy.udytils.trace.rules.scoped
import kotlinx.coroutines.flow.first

sealed interface SampleEvent {
    data class Formatting(val count: Int) : SampleEvent
    data object Formatted : SampleEvent
    data class LoaderCreated(val label: String) : SampleEvent
    data class Loaded(val label: String) : SampleEvent
    data class GreetStarted(val name: String, val greeting: String?) : SampleEvent
    data class Greeted(val name: String, val greeting: String?) : SampleEvent
    data class CurrentGreeting(val greeting: String?) : SampleEvent
}

sealed interface SampleMetric {
    data class Format(val succeeded: Boolean) : SampleMetric
    data object GreetingsLoad : SampleMetric
    data object LoadTime : SampleMetric
}

fun sampleAnalyticsRules() = analyticsRules<SampleEvent> {
    var formatting = 0
    context { mapOf("session" to "s1") }
    onCalled(Formatter::format).event(Trigger.Start) { SampleEvent.Formatting(++formatting) }
    onCalled(Formatter::format).event { SampleEvent.Formatted }
    onConstructed(Loader::class).event { SampleEvent.LoaderCreated(it.label) }
    onConstructed(Loader::class).scoped { it.scope }.event { loader ->
        loader.state.first { it != null }
        SampleEvent.Loaded(loader.label)
    }
}

fun sampleMetricRules() = metricRules<SampleMetric> {
    onCalled(Formatter::format).timing { SampleMetric.Format(succeeded = span.outcome == Outcome.Returned) }
    onCalled(FlowOfGreetings::invoke).firstValue { SampleMetric.GreetingsLoad }
    onCalled(Loader::load).scoped { it.scope }.timing { loader ->
        loader.state.first { it != null }
        SampleMetric.LoadTime
    }
}

fun sampleArgumentRules() = analyticsRules<SampleEvent> {
    onCalled(Greet::invoke).event(Trigger.Start) { SampleEvent.GreetStarted(args.first, result) }
    onCalled(Greet::invoke).event {
        val (name) = args
        SampleEvent.Greeted(name, result)
    }
    onCalled(GreetingListModel::currentGreeting).event { SampleEvent.CurrentGreeting(result) }
}
