# urpc — typed RPC over Ktor

Define a service as an annotated Kotlin interface; KSP generates the client caller and
the server binding. Unary calls travel as plain HTTP POSTs; streaming and bidirectional
calls are **multiplexed over a single WebSocket** with lazy connect, exponential-backoff
reconnect, and per-call isolation (one call's failure never disturbs the others).

## Artifacts

| Coordinates (`dev.isaacudy.udytils:*`) | What it is | Where it goes |
|---|---|---|
| `urpc-protocol` | The `@Urpc` annotation, descriptors, frames, interceptors, errors | your shared contract module (KMP) |
| `urpc-processor` | KSP processor generating bindings + `create()` factories | `ksp(...)` in the contract module |
| `urpc-client` | Ktor-backed `UrpcClientFactory` (HTTP + reconnecting WebSocket) | client apps (KMP) |
| `urpc-server` | `Route.urpc { }` Ktor routing + error mapping | your Ktor server (JVM) |
| `urpc-koin` | Per-call Koin scope + service registration DSL | server, if you use Koin |

## 1. Define the contract (shared KMP module)

```kotlin
plugins { id("com.google.devtools.ksp") }
dependencies {
    implementation("dev.isaacudy.udytils:urpc-protocol:<version>")
    ksp("dev.isaacudy.udytils:urpc-processor:<version>")
}
```

```kotlin
@Urpc("chat")
interface ChatService {
    suspend fun send(request: SendMessageRequest): SendMessageResponse   // unary
    fun updates(request: SubscribeRequest): Flow<ChatEvent>              // server-streaming
    fun session(requests: Flow<ClientEvent>): Flow<ServerEvent>          // bidirectional
}
```

Request/response types are `@Serializable`. From this the processor generates
`ChatServiceUrpcBinding` (server), a client implementation, and a
`UrpcClientFactory.create<ChatService>()` extension. `@UrpcWireName("...")` pins a
function's wire name so source renames don't break deployed clients.

## 2. Serve it (Ktor, JVM)

```kotlin
install(WebSockets)
routing {
    urpc { call ->
        val service = services.firstOrNull { it.accepts(call) }
            ?: return@urpc call.applicationCall.respond(HttpStatusCode.NotFound)
        service.handle(call)
    }
}
```

This registers `POST /services/{name}` for unary calls and the `/urpc` WebSocket for
streaming. Handler exceptions map to HTTP statuses via `ServiceErrorMapper`
(`UnauthorizedException` → 401 out of the box); streaming failures are delivered to the
client as typed error frames that surface as `ServiceException`.

With Koin, `urpc-koin` opens one scope per call (surviving the WebSocket upgrade, unlike
Koin's request scope) and `urpcService(::ChatServiceUrpcBinding)` registers bindings so
`call.scope.getAll<UrpcService>()` finds them.

## 3. Call it (any client target)

```kotlin
val httpClient = HttpClient { install(WebSockets) }
val urpc = httpClient.urpcClient(
    baseUrl = "https://api.example.com",
    interceptors = listOf(bearerTokenInterceptor(authTokenFlow)),
)
val chat: ChatService = urpc.create()

chat.send(SendMessageRequest("hello"))          // HTTP POST
chat.updates(SubscribeRequest(roomId)).collect { render(it) }   // over the shared WS
```

Behaviour worth knowing:

- The WebSocket opens lazily on the first streaming call and closes when the last call
  ends; if it drops, server-streaming calls are transparently re-opened (an idempotent
  replay of the request) while bidirectional calls fail loudly with
  `UrpcConnectionClosedException` — resuming a half-consumed bidirectional exchange is
  not generally safe, so that decision is left to the caller.
- Interceptors run per call and may suspend to gate it — `bearerTokenInterceptor` holds
  streaming calls until a token is available, so a logged-out client never opens the
  socket. Unary calls get the same metadata as request headers, plus 401-triggered token
  refresh with a single retry.
- Bidirectional calls are consumer-driven: the call stays open while you keep collecting.

## Example

[`urpc/sample`](sample) is a complete contract module; its round-trip tests
(`ExampleServiceRoundTripTest`, `ExampleServiceWithKoinTest`) run every call shape —
unary, streaming, bidirectional, wire renames, typed stream errors, and multiplexed
failure isolation — through Ktor's test host and are the best end-to-end reference.
