package sample

import dev.isaacudy.udytils.trace.SiteTable
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.rules.InMemoryAnalyticsSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ArgumentRulesTest {
    private val analytics = InMemoryAnalyticsSink<SampleEvent>()
    private val registrations = mutableListOf<AutoCloseable>()

    @AfterTest
    fun removeSinks() = registrations.forEach { it.close() }

    private fun register(delivery: CoroutineScope) {
        registrations += Trace.addSink(sampleArgumentRules().sink(SiteTable { emptyList() }, analytics, delivery))
    }

    @Test
    fun aRuleReadsTheArgumentsOfAFunInterfaceLambdaAndAnOverride() = runTest {
        register(CoroutineScope(Dispatchers.Unconfined))
        GreetingRepository().greet.invoke("ann")
        GreetImpl().invoke("bo")

        assertEquals(
            listOf(
                SampleEvent.GreetStarted("ann", null),
                SampleEvent.Greeted("ann", "hello ann"),
                SampleEvent.GreetStarted("bo", null),
                SampleEvent.Greeted("bo", "hi bo"),
            ),
            analytics.events.value,
        )
    }

    @Test
    fun aRuleReadsTheResultOfASuspendCall() = runTest {
        register(CoroutineScope(Dispatchers.Unconfined))
        val model = GreetingListModel(this, GreetingFlowRepository(GreetingSource(), Formatter()).flowOfGreetings, Recorder())
        model.currentGreeting(GreetingSource())

        assertEquals(listOf<SampleEvent>(SampleEvent.CurrentGreeting("now")), analytics.events.value)
    }

    @Test
    fun theSinkReceivesEventsInTheDeliveryScopeNotOnTheCallingThread() = runTest {
        register(CoroutineScope(StandardTestDispatcher(testScheduler)))
        GreetImpl().invoke("cy")
        assertEquals(emptyList(), analytics.events.value)

        testScheduler.runCurrent()
        assertEquals(listOf(SampleEvent.GreetStarted("cy", null), SampleEvent.Greeted("cy", "hi cy")), analytics.events.value)
    }
}
