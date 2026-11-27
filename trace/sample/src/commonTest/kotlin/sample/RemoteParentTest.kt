package sample

import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.remoteParent
import dev.isaacudy.udytils.trace.traceparent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RemoteParentTest {
    private val sink = InMemorySpanSink { if (it.name == "sample.GreetingStore.read") SiteFlags.RECORD or SiteFlags.ROOT else SiteFlags.RECORD }
    private val registration = Trace.addSink(sink)

    @AfterTest
    fun removeSink() = registration.close()

    private val header = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"

    @Test
    fun aRootSiteUnderARemoteParentContinuesTheRemoteTrace() = runTest {
        withContext(remoteParent(header)!!) { GreetingStore().read() }
        val span = sink.spans.single { it.site.name == "sample.GreetingStore.read" }
        assertEquals("0af7651916cd43dd8448eb211c80319c", span.traceId.toString())
        assertEquals("b7ad6b7169203331", span.parentSpanId!!.toULong().toString(16))
    }

    @Test
    fun aSpansTraceparentNamesItsTraceAndSpan() = runTest {
        val parent = remoteParent(header)!!.span
        assertEquals(header, parent.traceparent)
    }

    @Test
    fun aMalformedTraceparentHasNoRemoteParent() {
        assertNull(remoteParent("01-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"))
        assertNull(remoteParent("00-00000000000000000000000000000000-b7ad6b7169203331-01"))
        assertNull(remoteParent("00-xyz-b7ad6b7169203331-01"))
    }

    @Test
    fun uninstrumentedCodeReadsTheSpanOfItsInstrumentedCaller() = runTest {
        val read = SpanReaderCaller(SpanReader()).call()
        assertSame(sink.spans.single { it.site.name == "sample.SpanReaderCaller.call" }, read)
    }
}
