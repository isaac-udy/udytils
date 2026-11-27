package sample

import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.SpanKind
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.remoteParent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowTest {
    private val sink = InMemorySpanSink { if (it.name == "sample.GreetingListModel.onStart") SiteFlags.RECORD or SiteFlags.ROOT else SiteFlags.RECORD }
    private val registration = Trace.addSink(sink)

    @AfterTest
    fun removeSink() = registration.close()

    private fun spans(name: String, kind: SpanKind = SpanKind.Call) = sink.spans.filter { it.site.name == name && it.kind == kind }

    private fun model() = GreetingFlowRepository(GreetingSource(), Formatter()).flowOfGreetings

    @Test
    fun eachCollectionIsASpanUnderTheCollectorWithItsUpstreamNested() = runTest {
        val recorder = Recorder()
        GreetingListModel(this, model(), recorder).onStart().join()
        assertEquals(listOf("A!", "B!", "C!"), recorder.received)

        val start = spans("sample.GreetingListModel.onStart").single()
        val collection = spans("sample.GreetingFlowRepository.flowOfGreetings", SpanKind.Collect).single()
        val upstream = spans("sample.GreetingSource.observe", SpanKind.Collect).single()
        assertEquals(start, collection.parent)
        assertEquals(collection, upstream.parent)
        assertEquals(3, collection.attributes[Span.EMISSIONS])
        assertEquals(Outcome.Returned, collection.outcome)
        assertTrue(spans("sample.GreetingFlowRepository.flowOfGreetings").isEmpty(), "the call that returns the Flow records no span")

        val formats = spans("sample.Formatter.format")
        assertEquals(3, formats.size)
        assertTrue(formats.all { it.parent == collection }, "operators applied after observe() run under the repository collection: ${formats.map { it.parent }}")

        val records = spans("sample.Recorder.record")
        assertTrue(records.all { it.parent == start }, "the downstream collector runs under the collector's span: ${records.map { it.parent }}")
    }

    @Test
    fun anOperatorAfterConflateRunsUnderTheCollectionNotUnderTheCoroutinesRemoteParent() = runTest {
        val remote = remoteParent("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")!!
        withContext(remote) { RowReader(ConflatedRows(RowStore())).read() }

        val read = spans("sample.RowReader.read").single()
        val collection = spans("sample.ConflatedRows.observe", SpanKind.Collect).single()
        val list = spans("sample.RowStore.list").single()
        assertEquals(remote.span, read.parent)
        assertEquals(read, collection.parent)
        assertEquals(collection, list.parent)
    }

    @Test
    fun theFirstValueSpanMeasuresTheTimeToTheFirstEmission() = runTest {
        GreetingListModel(this, model(), Recorder()).onStart().join()
        val collection = spans("sample.GreetingFlowRepository.flowOfGreetings", SpanKind.Collect).single()
        val firstValue = spans("sample.GreetingFlowRepository.flowOfGreetings", SpanKind.FirstValue).single()
        assertEquals(collection, firstValue.parent)
        assertTrue(firstValue.durationNanos in 0..collection.durationNanos)
    }

    @Test
    fun aCollectionThatStopsEarlyIsCancelled() = runTest {
        assertEquals("A!", GreetingListModel(this, model(), Recorder()).firstGreeting())
        val collection = spans("sample.GreetingFlowRepository.flowOfGreetings", SpanKind.Collect).single()
        assertEquals(Outcome.Cancelled, collection.outcome)
        assertEquals(1, collection.attributes[Span.EMISSIONS])
        assertEquals("sample.GreetingListModel.firstGreeting", collection.parent?.site?.name)
    }

    @Test
    fun aStateFlowReturnIsRecordedAsTheCallOnly() = runTest {
        GreetingListModel(this, model(), Recorder()).currentGreeting(GreetingSource())
        val call = spans("sample.GreetingSource.current").single()
        assertEquals("sample.GreetingListModel.currentGreeting", call.parent?.site?.name)
        assertTrue(spans("sample.GreetingSource.current", SpanKind.Collect).isEmpty())
    }
}
