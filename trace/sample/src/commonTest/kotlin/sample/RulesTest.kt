package sample

import dev.isaacudy.udytils.trace.SiteTable
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.rules.InMemoryAnalyticsSink
import dev.isaacudy.udytils.trace.rules.InMemoryMetricsSink
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RulesTest {
    private val rules = sampleRules()
    private val analytics = InMemoryAnalyticsSink<SampleEvent>()
    private val metrics = InMemoryMetricsSink()
    private val registration = Trace.addSink(rules.sink(SiteTable { emptyList() }, analytics, metrics))

    @AfterTest
    fun removeSink() = registration.close()

    @Test
    fun eventsFollowTheCallsAndTimingsCarryTheirKindAndOutcome() = runTest {
        val recorder = Recorder()
        GreetingListModel(this, GreetingFlowRepository(GreetingSource(), Formatter()).flowOfGreetings, recorder).onStart().join()

        assertEquals(
            listOf(SampleEvent.Formatting(1), SampleEvent.Formatted, SampleEvent.Formatting(2), SampleEvent.Formatted, SampleEvent.Formatting(3), SampleEvent.Formatted),
            analytics.events.value,
        )
        assertEquals(
            listOf("format" to "Call", "greetings" to "FirstValue", "format" to "Call", "format" to "Call"),
            metrics.timings.value.map { it.name to it.tags.getValue("kind") },
        )
        assertEquals(setOf("returned"), metrics.timings.value.map { it.tags.getValue("outcome") }.toSet())
    }
}
