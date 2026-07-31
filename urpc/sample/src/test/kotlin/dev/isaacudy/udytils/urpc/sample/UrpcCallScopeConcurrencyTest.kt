package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor
import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcServerCall
import dev.isaacudy.udytils.urpc.koin.UrpcCall
import dev.isaacudy.udytils.urpc.koin.urpcWithKoin
import io.ktor.server.application.install as installApplicationPlugin
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import org.koin.core.Koin
import org.koin.core.qualifier.TypeQualifier
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards `urpcWithKoin`'s cold-start registration of the per-call [UrpcServerCall] definition.
 *
 * `Scope.declare` registers the backing factory on first use with a check-then-act guarded only by
 * the declaring scope's own lock — a different object per call — so before the mount-time
 * reservation existed, calls opening together against a fresh graph each built their own factory,
 * the registry kept whichever saved last, and every other call's declared value was orphaned:
 * `get<UrpcServerCall>()` failed with `MissingScopeValueException` naming another call's scope id,
 * killing everything call-scoped (auth first) in a cold server's first concurrent burst.
 *
 * Driven at the scope level rather than over the wire because the failure is a data race on the
 * first declare into a fresh graph, and it closes for good the moment any one call wins it — a
 * wire-level test collides often enough to fail on a real cold server but never reliably enough to
 * pin a test to. The barrier makes every call reach the declare together, against the graph
 * exactly as `urpcWithKoin`'s mount left it.
 */
class UrpcCallScopeConcurrencyTest {

    @Test
    fun callsOpeningAtTheSameMomentEachKeepTheirOwnScopeValue() = testApplication {
        var graph: Koin? = null
        application {
            // A consumer-style module: the UrpcCall scope is declared (as any real consumer's
            // feature module does) but nothing pre-registers UrpcServerCall — that reservation
            // is urpcWithKoin's job at mount time, which is what this test pins.
            installApplicationPlugin(Koin) { modules(module { scope<UrpcCall> { } }) }
            installApplicationPlugin(ServerWebSockets)
            routing { urpcWithKoin() }
            graph = getKoin()
        }
        startApplication()
        val koin = checkNotNull(graph) { "the application block did not run" }

        val calls = 8
        val barrier = CyclicBarrier(calls)
        val failures = Collections.synchronizedList(mutableListOf<String>())

        coroutineScope {
            (1..calls).map { call ->
                async(Dispatchers.IO) {
                    // Opens the per-call scope the way urpcWithKoin does — create, declare the
                    // call into it, resolve — held at the barrier so every call reaches the
                    // declare together.
                    val scope = koin.createScope("urpc-cold-call-$call", TypeQualifier(UrpcCall::class), null)
                    try {
                        barrier.await()
                        scope.declare<UrpcServerCall>(StubCall(call))
                        // Checking identity, not just that something resolved: the failure mode
                        // is one call's value replacing another's, so a scope handing back a
                        // different call's metadata is the same defect landing silently.
                        val resolved = scope.get<UrpcServerCall>()
                        assertEquals(
                            mapOf("caller" to "call-$call"),
                            resolved.metadata,
                            "call $call resolved another call's metadata",
                        )
                    } catch (t: Throwable) {
                        failures += "call $call: ${t::class.simpleName}: ${t.message}"
                    } finally {
                        scope.close()
                    }
                }
            }.awaitAll()
        }

        assertEquals(emptyList(), failures.toList(), "calls opening together failed to resolve their call scope")
    }

    /** Stands in for the transport's call object; only [metadata] is read on this path. */
    private class StubCall(id: Int) : UrpcServerCall {
        override val wireName: String = "example.stub"
        override val metadata: Map<String, String> = mapOf("caller" to "call-$id")

        override suspend fun <Req, Res> handleUnary(
            descriptor: ServiceDescriptor<Req, Res>,
            invoke: suspend (Req) -> Res,
        ) = error("not dispatched in this test")

        override suspend fun <Req, Res> handleStreaming(
            descriptor: StreamingServiceDescriptor<Req, Res>,
            invoke: (Req) -> Flow<Res>,
        ) = error("not dispatched in this test")

        override suspend fun <Req, Res> handleBidirectional(
            descriptor: BidirectionalServiceDescriptor<Req, Res>,
            invoke: (Flow<Req>) -> Flow<Res>,
        ) = error("not dispatched in this test")
    }
}
