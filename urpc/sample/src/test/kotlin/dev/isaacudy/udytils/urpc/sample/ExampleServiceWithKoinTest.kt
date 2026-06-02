package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.UrpcClientInterceptor
import dev.isaacudy.udytils.urpc.UrpcServerCall
import dev.isaacudy.udytils.urpc.UrpcService
import dev.isaacudy.udytils.urpc.client.urpcClient
import dev.isaacudy.udytils.urpc.koin.UrpcCall
import dev.isaacudy.udytils.urpc.koin.bindService
import dev.isaacudy.udytils.urpc.koin.urpcService
import dev.isaacudy.udytils.urpc.koin.urpcWithKoin
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install as installApplicationPlugin
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the `urpcWithKoin` per-call scope ([UrpcCall]) end to end.
 *
 * The headline behaviour, and the reason `UrpcCall` exists rather than reusing Koin's
 * Ktor request-scope plugin: a per-call Koin scope is opened for **streaming** calls too,
 * not just unary ones. Koin's request scope is tied to the HTTP request and does not
 * survive the WebSocket upgrade — so it cannot scope streaming calls — whereas `UrpcCall`
 * is driven by the urpc call lifecycle (one scope per `Open`).
 *
 * Validates that:
 *  - the generated `ExampleServiceUrpcBinding` resolves from a `scope<UrpcCall> { ... }`
 *    block for both unary calls and WebSocket-backed streaming calls;
 *  - per-`Open` metadata reaches a scoped service via `get<UrpcServerCall>().metadata`
 *    (the mechanism per-call auth such as `SessionAuth` relies on); and
 *  - a fresh scoped impl is built per call (scope opens and closes each time).
 */
class ExampleServiceWithKoinTest {

    /**
     * Reads per-call state from the scoped [UrpcServerCall] — `metadata["caller"]` (auth/trace
     * style) and, for streaming, `metadata["from"]` to prove `Open`-frame metadata reached this
     * impl. Records how many instances have been built so per-call freshness is observable.
     */
    private class ExampleServiceImpl(
        private val call: UrpcServerCall,
        // Injected via get<ApplicationCall>(), which resolves from the scope SOURCE (set by
        // urpcWithKoin to the call's ApplicationCall, mirroring koin-ktor's RequestScope). This is
        // the exact path a real per-call dependency like RequestInfo(get<ApplicationCall>()) uses;
        // requiring it here is a regression guard for scope-source resolution on BOTH unary and
        // streaming calls (the WS connection's ApplicationCall must resolve too).
        private val applicationCall: ApplicationCall,
        constructions: AtomicInteger,
    ) : ExampleService {
        private val buildNo = constructions.incrementAndGet()

        override suspend fun sayHello(request: SayHelloRequest): SayHelloResponse {
            val caller = call.metadata["caller"] ?: "anonymous"
            // Touch the ApplicationCall so the injection can't be optimised away.
            check(applicationCall.request.local.method.value.isNotEmpty())
            return SayHelloResponse(greeting = "Hello, ${request.name}! (caller=$caller, build=$buildNo)")
        }

        override suspend fun ping(): PongResponse = PongResponse(message = "pong")

        override fun countdown(request: CountdownRequest): Flow<CountdownTick> = flow {
            // Reaching this confirms get<ApplicationCall>() resolved for a STREAMING call too
            // (the bug that broke Reglyph: RequestInfo on a streaming flowOfUserProfile call).
            check(applicationCall.request.local.method.value.isNotEmpty())
            // Per-Open metadata may override the start — proves the Open-frame metadata reached
            // this scoped *streaming* impl via UrpcServerCall.metadata (the SessionAuth path).
            val from = call.metadata["from"]?.toInt() ?: request.from
            for (i in from downTo 0) emit(CountdownTick(remaining = i))
        }

        override fun echoStream(requests: Flow<EchoMessage>): Flow<EchoMessage> =
            requests.map { EchoMessage(text = "echo:${it.text}") }

        override fun ambiguousStream(request: AmbiguousStreamRequest): Flow<AmbiguousResponse> = flow {}
        override fun failingStream(request: FailingStreamRequest): Flow<EchoMessage> = flow {}
        override suspend fun functionThatGotRenamedInSource(request: RenamedRequest): RenamedResponse =
            RenamedResponse(echoedPayload = "echoed:${request.payload}")
    }

    /**
     * A second [UrpcService] registered alongside [ExampleServiceUrpcBinding] in the same scope.
     * Regression guard: when more than one UrpcService shares a scope, each must be bound by a
     * DISTINCT primary type (`scoped { ... } bind UrpcService::class`) — registering both as
     * `scoped<UrpcService> { ... }` collides on the same definition key, so they override each
     * other and `getAll<UrpcService>()` returns only one. With this present, every test below
     * exercises multi-binding dispatch; if it regressed, ExampleService calls would 404.
     */
    private class OtherUrpcService : UrpcService {
        override fun accepts(call: UrpcServerCall): Boolean = false
        override suspend fun handle(call: UrpcServerCall) = error("not reachable")
    }

    private fun exampleModule(constructions: AtomicInteger) = module {
        // One UrpcCall scope per call — per HTTP request (unary) and per Open (streaming).
        // The UrpcServerCall is declared into the scope by urpcWithKoin, so get<UrpcServerCall>()
        // resolves here exactly as SessionAuth would in a real service module.
        scope<UrpcCall> {
            // The chained `bindService(::XUrpcBinding)` helper: bind the impl, then expose it over
            // urpc. OtherUrpcService is a second UrpcService in the SAME scope — both must coexist
            // (regression guard for the definition-key collision), which `bindService` ensures by
            // registering each binding under its own concrete type.
            scoped {
                ExampleServiceImpl(get<UrpcServerCall>(), get<ApplicationCall>(), constructions)
            }.bind(ExampleService::class)
                .bindService(::ExampleServiceUrpcBinding)
            scoped { OtherUrpcService() } bind UrpcService::class
        }
    }

    /** Same as [exampleModule] but registers the binding via the standalone [urpcService] helper. */
    private fun exampleModuleStandalone(constructions: AtomicInteger) = module {
        scope<UrpcCall> {
            scoped {
                ExampleServiceImpl(get<UrpcServerCall>(), get<ApplicationCall>(), constructions)
            } bind ExampleService::class
            urpcService(::ExampleServiceUrpcBinding)
            scoped { OtherUrpcService() } bind UrpcService::class
        }
    }

    private fun metadataInterceptor(vararg entries: Pair<String, String>) =
        UrpcClientInterceptor { context -> entries.forEach { (k, v) -> context.metadata[k] = v } }

    @Test
    fun unaryCallResolvesBindingFromUrpcCallScope() = testApplication {
        val constructions = AtomicInteger(0)
        application {
            installApplicationPlugin(Koin) { modules(exampleModule(constructions)) }
            installApplicationPlugin(ServerWebSockets)
            routing { urpcWithKoin() }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        assertTrue(service.sayHello(SayHelloRequest("Isaac")).greeting.startsWith("Hello, Isaac!"))
        assertEquals("pong", service.ping().message)
    }

    @Test
    fun streamingCallResolvesBindingFromPerOpenUrpcCallScope() = testApplication {
        // The case the old test recorded as a VERIFIED LIMITATION (returned empty -> 404 ->
        // reconnect spin). With UrpcCall it works: the per-Open scope resolves the binding.
        val constructions = AtomicInteger(0)
        application {
            installApplicationPlugin(Koin) { modules(exampleModule(constructions)) }
            installApplicationPlugin(ServerWebSockets)
            routing { urpcWithKoin() }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        val ticks = withTimeout(10_000) { service.countdown(CountdownRequest(from = 3)).toList() }
        assertEquals(listOf(3, 2, 1, 0), ticks.map { it.remaining })
    }

    @Test
    fun perOpenMetadataReachesScopedStreamingService() = testApplication {
        // Proves Open-frame metadata reaches a scoped streaming impl via UrpcServerCall.metadata —
        // the exact path per-Open auth (SessionAuth) uses. The interceptor sets from=2, so the
        // stream emits [2,1,0] even though the request asks for from=0.
        val constructions = AtomicInteger(0)
        application {
            installApplicationPlugin(Koin) { modules(exampleModule(constructions)) }
            installApplicationPlugin(ServerWebSockets)
            routing { urpcWithKoin() }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(
            baseUrl = "",
            interceptors = listOf(metadataInterceptor("from" to "2")),
        ).create<ExampleService>()

        val ticks = withTimeout(10_000) { service.countdown(CountdownRequest(from = 0)).toList() }
        assertEquals(listOf(2, 1, 0), ticks.map { it.remaining })
    }

    @Test
    fun standaloneUrpcServiceHelperResolvesAlongsideAnotherBinding() = testApplication {
        // Proves the standalone `urpcService(::Binding)` helper registers a binding that coexists
        // with another UrpcService in the same scope (OtherUrpcService) and dispatches correctly.
        val constructions = AtomicInteger(0)
        application {
            installApplicationPlugin(Koin) { modules(exampleModuleStandalone(constructions)) }
            installApplicationPlugin(ServerWebSockets)
            routing { urpcWithKoin() }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        assertEquals("pong", service.ping().message)
        val ticks = withTimeout(10_000) { service.countdown(CountdownRequest(from = 2)).toList() }
        assertEquals(listOf(2, 1, 0), ticks.map { it.remaining })
    }

    @Test
    fun eachCallGetsAFreshScopedImpl() = testApplication {
        // A shared construction counter: each call must build its own ExampleServiceImpl, which
        // only happens if a fresh UrpcCall scope opens (and closes) per call.
        val constructions = AtomicInteger(0)
        application {
            installApplicationPlugin(Koin) { modules(exampleModule(constructions)) }
            installApplicationPlugin(ServerWebSockets)
            routing { urpcWithKoin() }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        val first = service.sayHello(SayHelloRequest("Alice")).greeting
        val second = service.sayHello(SayHelloRequest("Bob")).greeting
        assertTrue(first.startsWith("Hello, Alice!"), first)
        assertTrue(second.startsWith("Hello, Bob!"), second)
        assertTrue("build=1" in first, first)
        assertTrue("build=2" in second, second)
    }
}
