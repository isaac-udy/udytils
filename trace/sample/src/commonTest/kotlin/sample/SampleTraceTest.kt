package sample

import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.Trace
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SampleTraceTest {
    private val sink = InMemorySpanSink { if (it.name.endsWith(".<init>")) 0 else SiteFlags.RECORD }
    private val registration = Trace.addSink(sink)

    @AfterTest
    fun removeSink() = registration.close()

    @Test
    fun aClassMethodAndAFunInterfaceLambdaNest() {
        assertEquals("hello sample", Screen(GreetingRepository().greet).onGreetClicked())
        val (lambda, screen) = sink.spans
        assertEquals("sample.GreetingRepository.greet", lambda.site.name)
        assertEquals("sample.Screen.onGreetClicked", screen.site.name)
        assertEquals(screen, lambda.parent)
        assertNull(Trace.currentSpan)
    }

    @Test
    fun anOverrideSharesTheLambdasDeclarationId() {
        GreetImpl().invoke("a")
        GreetingRepository().greet.invoke("b")
        val (override, lambda) = sink.spans
        assertEquals(override.site.declarationId, lambda.site.declarationId)
    }

    @Test
    fun aThrownErrorIsRecorded() {
        runCatching { failing() }
        assertIs<Outcome.Threw>(sink.spans.single().outcome)
    }
}
