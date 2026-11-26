package sample

import dev.isaacudy.udytils.trace.SiteTable
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.rules.InMemoryAnalyticsSink
import dev.isaacudy.udytils.trace.rules.InMemoryMetricsSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InstanceRulesTest {
    private val analytics = InMemoryAnalyticsSink<SampleEvent>()
    private val metrics = InMemoryMetricsSink<SampleMetric>()
    private val registrations = listOf(
        Trace.addSink(sampleAnalyticsRules().sink(SiteTable { emptyList() }, analytics)),
        Trace.addSink(sampleMetricRules().sink(SiteTable { emptyList() }, metrics)),
    )

    @AfterTest
    fun removeSinks() = registrations.forEach { it.close() }

    @Test
    fun aConstructionRuleReceivesTheInstanceAndAScopedRuleWaitsInTheInstancesScope() = runTest {
        val loader = Loader(this, "home")
        assertEquals(listOf<SampleEvent>(SampleEvent.LoaderCreated("home")), analytics.events.value)

        loader.load()
        advanceUntilIdle()

        assertEquals(listOf(SampleEvent.LoaderCreated("home"), SampleEvent.Loaded("home")), analytics.events.value)
        assertEquals(listOf<SampleMetric>(SampleMetric.LoadTime), metrics.timings.value.map { it.metric })
    }

    @Test
    fun aCancelledScopeRecordsNothing() = runTest {
        val scope = CoroutineScope(coroutineContext + Job())
        val loader = Loader(scope, "abandoned")
        scope.cancel()
        loader.load()
        advanceUntilIdle()

        assertEquals(listOf<SampleEvent>(SampleEvent.LoaderCreated("abandoned")), analytics.events.value)
        assertEquals(emptyList(), metrics.timings.value)
    }

    @Test
    fun aSubclassConstructionIsNotAConstructionOfItsSuperclass() = runTest {
        SpecialLoader(this)
        advanceUntilIdle()
        assertEquals(emptyList(), analytics.events.value)
    }
}
