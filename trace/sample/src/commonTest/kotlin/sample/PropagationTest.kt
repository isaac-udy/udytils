@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package sample

import dev.isaacudy.udytils.coroutines.JobManager
import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.Trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropagationTest {
    private val intents = setOf("sample.GreetingModel.onGreetClicked", "sample.GreetingModel.onGreetClickedThroughJobManager")
    private val sink = InMemorySpanSink { if (it.name in intents) SiteFlags.RECORD or SiteFlags.ROOT else SiteFlags.RECORD }
    private val registration = Trace.addSink(sink)

    @AfterTest
    fun removeSink() = registration.close()

    private fun span(name: String): Span = sink.spans.single { it.site.name == name }

    private fun parentName(name: String): String? = span(name).parent?.site?.name

    @Test
    fun anIntentNestsTheCapabilityAndTheRepositoryCall() = runTest {
        val model = GreetingModel(this, LoadingRepository(GreetingStore()).loadGreeting, JobManager(this))
        model.onGreetClicked().join()
        assertEquals("hello", model.greeting)
        assertEquals("sample.GreetingModel.onGreetClicked", parentName("sample.LoadingRepository.loadGreeting"))
        assertEquals("sample.LoadingRepository.loadGreeting", parentName("sample.GreetingStore.read"))
        assertEquals(span("sample.GreetingModel.onGreetClicked").traceId, span("sample.GreetingStore.read").traceId)
    }

    @Test
    fun anIntentNestsTheCapabilityThroughTheJobManager() = runTest {
        val model = GreetingModel(this, LoadingRepository(GreetingStore()).loadGreeting, JobManager(this))
        model.onGreetClickedThroughJobManager().join()
        assertEquals("sample.GreetingModel.onGreetClickedThroughJobManager", parentName("sample.LoadingRepository.loadGreeting"))
    }

    @Test
    fun aCallMadeWhileATracedCoroutineIsSuspendedStartsANewTrace() = runTest {
        val waiter = Waiter()
        val waiting = launch { waiter.waitForRelease() }
        runCurrent()
        GreetingModel(this, LoadGreeting { "x" }, JobManager(this)).onClick()
        assertNull(span("sample.GreetingModel.onClick").parent)
        assertNull(Trace.currentSpan)
        waiter.release.complete(Unit)
        waiting.join()
    }

    @Test
    fun aJobLaunchedByUntracedCodeWhileATracedCoroutineIsSuspendedStartsANewTrace() = runTest {
        val waiter = Waiter()
        val waiting = launch { waiter.waitForRelease() }
        runCurrent()
        val loadGreeting = LoadingRepository(GreetingStore()).loadGreeting
        JobManager(this).launchReplacing("callback") { loadGreeting.invoke() }.join()
        assertNull(parentName("sample.LoadingRepository.loadGreeting"))
        waiter.release.complete(Unit)
        waiting.join()
    }

    @Test
    fun scopeBuildersKeepTheCallerAsTheParent() = runTest {
        val builders = Builders(GreetingStore())
        builders.viaCoroutineScope()
        builders.viaWithContext(Dispatchers.Default)
        builders.viaWithTimeout()
        builders.viaFlowOn(Dispatchers.Default)
        val parents = sink.spans.filter { it.site.name == "sample.GreetingStore.read" }.map { it.parent?.site?.name }
        assertEquals(
            listOf("sample.Builders.viaCoroutineScope", "sample.Builders.viaWithContext", "sample.Builders.viaWithTimeout", "sample.Builders.viaFlowOn"),
            parents,
        )
    }

    @Test
    fun aCancelledCoroutineRunsATracedBodyUnchanged() = runTest {
        var result: Int? = null
        launch {
            cancel()
            result = valueInCancelledCoroutine()
        }.join()
        assertEquals(42, result)
    }

    @Test
    fun declarationIdOfMatchesTheLambdaSite() = runTest {
        LoadingRepository(GreetingStore()).loadGreeting.invoke()
        assertEquals(loadGreetingDeclarationId, span("sample.LoadingRepository.loadGreeting").site.declarationId)
    }
}
