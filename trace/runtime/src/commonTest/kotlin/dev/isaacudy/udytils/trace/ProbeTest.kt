@file:OptIn(TraceProbeApi::class)

package dev.isaacudy.udytils.trace

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class ProbeTest {
    private val registrations = mutableListOf<AutoCloseable>()

    @AfterTest
    fun removeSinks() {
        registrations.forEach { it.close() }
    }

    private fun sink(select: (TraceSite) -> Int = { SiteFlags.RECORD }) =
        InMemorySpanSink(select).also { registrations += Trace.addSink(it) }

    private fun <T> traced(site: TraceSite, receiver: Any? = null, block: () -> T): T {
        val span = traceEnter(site, receiver, null)
        try {
            return block()
        } catch (error: Throwable) {
            traceThrew(span, error)
            throw error
        } finally {
            traceExit(span)
        }
    }

    @Test
    fun aSiteWithoutASinkOpensNoSpan() {
        val site = TraceSite(1, 1, "untraced")
        assertEquals(0, site.flags)
        assertNull(traceEnter(site, null, null))
    }

    @Test
    fun addingASinkUpdatesTheFlagsOfRegisteredSites() {
        val site = TraceSite(2, 2, "registered")
        val sink = sink { if (it.name == "registered") SiteFlags.RECORD else 0 }
        assertEquals(SiteFlags.RECORD, site.flags)
        traced(site) {}
        assertEquals(listOf("registered"), sink.spans.map { it.site.name })
    }

    @Test
    fun nestedCallsShareTheTraceAndLinkTheirParent() {
        val sink = sink()
        val outer = TraceSite(3, 3, "outer")
        val inner = TraceSite(4, 4, "inner")
        traced(outer) { traced(inner) {} }
        val (innerSpan, outerSpan) = sink.spans
        assertEquals("inner", innerSpan.site.name)
        assertSame(outerSpan, innerSpan.parent)
        assertEquals(outerSpan.traceId, innerSpan.traceId)
        assertNull(Trace.currentSpan)
    }

    @Test
    fun aRootSiteStartsANewTrace() {
        val sink = sink { if (it.name == "root") SiteFlags.RECORD or SiteFlags.ROOT else SiteFlags.RECORD }
        traced(TraceSite(5, 5, "outer")) { traced(TraceSite(6, 6, "root")) {} }
        val (root, outer) = sink.spans
        assertNull(root.parent)
        assertEquals(false, root.traceId == outer.traceId)
    }

    @Test
    fun outcomesDistinguishReturnErrorAndCancellation() {
        val sink = sink()
        traced(TraceSite(7, 7, "returns")) {}
        runCatching { traced(TraceSite(8, 8, "throws")) { error("boom") } }
        runCatching { traced(TraceSite(9, 9, "cancels")) { throw CancellationException("replaced") } }
        val outcomes = sink.spans.associate { it.site.name to it.outcome }
        assertEquals(Outcome.Returned, outcomes["returns"])
        assertIs<Outcome.Threw>(outcomes["throws"])
        assertEquals(Outcome.Cancelled, outcomes["cancels"])
    }

    @Test
    fun aSpanThatStartsATraceLinksTheSpansThatTheResolversReturn() {
        val sink = sink()
        val owner = Any()
        val visit = Trace.startVisit("screen")!!
        registrations += Trace.linkRoots { receiver -> visit.takeIf { receiver === owner } }
        traced(TraceSite(10, 10, "intent"), receiver = owner) { traced(TraceSite(11, 11, "callee"), receiver = owner) {} }
        val (callee, intent) = sink.spans
        assertEquals(listOf(visit), intent.links)
        assertEquals(emptyList(), callee.links)
        Trace.endVisit(visit)
    }

    @Test
    fun aVisitStartsATraceAndEndsWhenEndVisitIsCalled() {
        val sink = sink { if (it.isVisit) SiteFlags.RECORD else 0 }
        val visit = Trace.startVisit("home")!!
        assertEquals(emptyList(), sink.spans)
        Trace.endVisit(visit)
        val ended = sink.spans.single()
        assertEquals(SpanKind.Visit, ended.kind)
        assertEquals("home", ended.site.name)
        assertNull(ended.parent)
        assertSame(ended.site, Trace.startVisit("home")!!.site)
    }

    @Test
    fun noVisitOpensWithoutASinkThatRecordsVisits() {
        sink { if (it.isVisit) 0 else SiteFlags.RECORD }
        assertNull(Trace.startVisit("unrecorded"))
    }
}
