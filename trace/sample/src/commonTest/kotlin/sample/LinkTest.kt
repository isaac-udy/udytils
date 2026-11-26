package sample

import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.Trace
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkTest {
    private val sink = InMemorySpanSink()
    private val registrations = mutableListOf(Trace.addSink(sink))

    @AfterTest
    fun removeSinks() = registrations.forEach { it.close() }

    @Test
    fun rootSpansLinkTheVisitOfTheirReceiver() = runTest {
        val model = GreetingListModel(this, GreetingFlowRepository(GreetingSource(), Formatter()).flowOfGreetings, Recorder())
        val visit = Trace.startVisit("sample.GreetingList")!!
        registrations += Trace.linkRoots { receiver -> visit.takeIf { receiver === model } }

        model.onStart().join()
        model.firstGreeting()
        Trace.endVisit(visit)

        val roots = sink.spans.filter { it.parent == null && it.site.name.startsWith("sample.GreetingListModel.") }
        assertEquals(
            mapOf("sample.GreetingListModel.onStart" to listOf(visit), "sample.GreetingListModel.firstGreeting" to listOf(visit)),
            roots.associate { it.site.name to it.links },
        )
        assertEquals(true, sink.spans.filter { it.parent != null }.all { it.links.isEmpty() })
    }
}
