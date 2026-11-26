package sample

import dev.isaacudy.udytils.trace.rules.Trigger
import dev.isaacudy.udytils.trace.rules.on
import dev.isaacudy.udytils.trace.rules.traceRules

sealed interface SampleEvent {
    data class Formatting(val count: Int) : SampleEvent
    data object Formatted : SampleEvent
}

fun sampleRules() = traceRules<SampleEvent> {
    var formatting = 0
    context { mapOf("session" to "s1") }
    event(on(Formatter::format), Trigger.Start) { SampleEvent.Formatting(++formatting) }
    event(on(Formatter::format)) { SampleEvent.Formatted }
    timing(on(Formatter::format), name = "format")
    firstValue(on(FlowOfGreetings::invoke), name = "greetings")
}
