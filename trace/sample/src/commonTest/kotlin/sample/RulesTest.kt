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
    private val analytics = InMemoryAnalyticsSink<SampleEvent>()
    private val metrics = InMemoryMetricsSink<SampleMetric>()
    private val registrations = listOf(
        Trace.addSink(sampleAnalyticsRules().sink(SiteTable { emptyList() }, analytics)),
        Trace.addSink(sampleMetricRules().sink(SiteTable { emptyList() }, metrics)),
    )

    @AfterTest
    fun removeSinks() = registrations.forEach { it.close() }

    @Test
    fun eventsFollowTheCallsAndTimingsCarryTheMetricsThatTheRulesBuild() = runTest {
        val recorder = Recorder()
        GreetingListModel(this, GreetingFlowRepository(GreetingSource(), Formatter()).flowOfGreetings, recorder).onStart().join()

        assertEquals(
            listOf(SampleEvent.Formatting(1), SampleEvent.Formatted, SampleEvent.Formatting(2), SampleEvent.Formatted, SampleEvent.Formatting(3), SampleEvent.Formatted),
            analytics.events.value,
        )
        val format = SampleMetric.Format(succeeded = true)
        assertEquals(
            listOf(format, SampleMetric.GreetingsLoad, format, format),
            metrics.timings.value.map { it.metric },
        )
    }
}
