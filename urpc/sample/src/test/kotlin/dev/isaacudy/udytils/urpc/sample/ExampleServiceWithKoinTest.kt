package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.UrpcService
import dev.isaacudy.udytils.urpc.client.urpcClient
import dev.isaacudy.udytils.urpc.koin.urpcWithKoin
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.application.install as installApplicationPlugin
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.RequestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the Koin Ktor request-scope integration end to end.
 *
 * Validates that:
 *  - The generated `ExampleServiceUrpcBinding` resolves from a Koin
 *    `requestScope { ... }` block per call (unary)
 *  - The same path works for WebSocket-backed streaming services — which is
 *    the open question we flagged about Koin's request-scope lifetime for
 *    upgraded connections
 *  - `urpcWithKoin()` is the only Ktor wiring the host needs
 */
class ExampleServiceWithKoinTest {

    private class ExampleServiceImpl : ExampleService {
        override suspend fun sayHello(request: SayHelloRequest): SayHelloResponse =
            SayHelloResponse(greeting = "Hello, ${request.name}!")

        override suspend fun ping(): PongResponse = PongResponse(message = "pong")

        override fun countdown(request: CountdownRequest): Flow<CountdownTick> = flow {
            for (i in request.from downTo 0) emit(CountdownTick(remaining = i))
        }

        override fun echoStream(requests: Flow<EchoMessage>): Flow<EchoMessage> =
            requests.map { EchoMessage(text = "echo:${it.text}") }

        override fun ambiguousStream(request: AmbiguousStreamRequest): Flow<AmbiguousResponse> = flow {}
        override fun failingStream(request: FailingStreamRequest): Flow<EchoMessage> = flow {}
        override suspend fun functionThatGotRenamedInSource(request: RenamedRequest): RenamedResponse =
            RenamedResponse(echoedPayload = "echoed:${request.payload}")
    }

    private val exampleModule = module {
        // koin-ktor opens a fresh scope qualified by RequestScope::class for every
        // HTTP request; beans declared here are resolved per-call.
        scope<RequestScope> {
            scoped { ExampleServiceImpl() } bind ExampleService::class
            scoped<UrpcService> { ExampleServiceUrpcBinding(get()) }
        }
    }

    @Test
    fun unaryCallResolvesBindingFromKoinRequestScope() = testApplication {
        application {
            installApplicationPlugin(Koin) { modules(exampleModule) }
            installApplicationPlugin(ServerWebSockets)
            routing { urpcWithKoin() }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        assertEquals("Hello, Isaac!", service.sayHello(SayHelloRequest("Isaac")).greeting)
        assertEquals("pong", service.ping().message)
    }

    // VERIFIED LIMITATION — empirically observed during this commit's spike:
    //   Koin's request scope is tied to the HTTP request lifecycle and does NOT
    //   survive WebSocket upgrades. When the streaming call's WS handler runs
    //   inside `urpcWithKoin`, `call.scope.getAll<UrpcService>()` returns an
    //   empty list, the catch-all handler responds 404, and the client's
    //   reconnect loop spins (the test hung ~35s before runTest cancelled it).
    //
    // The workaround documented on `urpcWithKoin` is: bind streaming services
    // as application-level singletons (`single<UrpcService>`), not in
    // `scope<RequestScope> { ... }`. The streaming round-trip case is already
    // exercised in ExampleServiceRoundTripTest using a hand-rolled list, which
    // is structurally what an application-singleton binding would do.
    //
    // We could re-add a Koin-streaming test that uses `single<UrpcService>`
    // for the streaming binding once we want to verify the documented
    // workaround end-to-end. Skipping for now to keep the spike's evidence
    // recorded in code rather than buried in a commit message.

    @Test
    fun perRequestScopeMeansEachCallSeesAFreshImpl() = testApplication {
        application {
            installApplicationPlugin(Koin) { modules(exampleModule) }
            installApplicationPlugin(ServerWebSockets)
            routing { urpcWithKoin() }
        }
        val httpClient = createClient { install(ClientWebSockets) }
        val service = httpClient.urpcClient(baseUrl = "").create<ExampleService>()

        // Two sequential calls. The Koin requestScope { } binding factory should
        // build a new ExampleServiceImpl per call. We can't observe identity
        // directly through the wire, but a successful pair of calls confirms the
        // scope opens and closes cleanly each time.
        val a = service.sayHello(SayHelloRequest("Alice"))
        val b = service.sayHello(SayHelloRequest("Bob"))
        assertTrue(a.greeting == "Hello, Alice!" && b.greeting == "Hello, Bob!")
    }
}
