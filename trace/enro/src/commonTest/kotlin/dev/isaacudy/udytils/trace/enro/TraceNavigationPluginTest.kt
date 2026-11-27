package dev.isaacudy.udytils.trace.enro

import androidx.lifecycle.ViewModel
import dev.enro.NavigationKey
import dev.enro.navigationHandle
import dev.enro.test.createTestNavigationHandle
import dev.enro.test.putNavigationHandleForViewModel
import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.SiteTable
import dev.isaacudy.udytils.trace.SpanKind
import dev.isaacudy.udytils.trace.Trace
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@Serializable
data object HomeKey : NavigationKey

class HomeViewModel : ViewModel() {
    val navigation by navigationHandle<HomeKey>()
}

class PlainViewModel : ViewModel()

class TraceNavigationPluginTest {
    private val sink = InMemorySpanSink { if (it.isVisit) SiteFlags.RECORD else 0 }
    private val registration = Trace.addSink(sink)
    private val plugin = TraceNavigationPlugin()

    @AfterTest
    fun removeSink() = registration.close()

    @Test
    fun aDestinationRecordsOneVisitFromOpenToClose() {
        val handle = createTestNavigationHandle(HomeKey)
        plugin.onOpened(handle)
        plugin.onActive(handle)
        plugin.onClosed(handle)
        plugin.onClosed(handle)

        val visit = sink.spans.single()
        assertEquals(SpanKind.Visit, visit.kind)
        assertEquals(true, onScreen(HomeKey::class).matches(visit.site, SiteTable { emptyList() }))
    }

    @Test
    fun activationOpensTheVisitOfARestoredDestination() {
        val handle = createTestNavigationHandle(HomeKey)
        plugin.onActive(handle)
        plugin.onClosed(handle)
        assertEquals(1, sink.spans.size)
    }

    @Test
    fun aViewModelResolvesToTheVisitOfItsDestination() {
        val handle = putNavigationHandleForViewModel<HomeViewModel, HomeKey>(HomeKey)
        val viewModel = HomeViewModel()
        plugin.onOpened(handle)

        val visit = plugin.visitOf(viewModel)
        plugin.onClosed(handle)
        assertSame(sink.spans.single(), visit)
    }

    @Test
    fun aReceiverWithoutADestinationResolvesToNoVisit() {
        assertNull(plugin.visitOf(PlainViewModel()))
        assertNull(plugin.visitOf(Any()))
    }
}
