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
| `urpc-client-rest` | `UrpcClientFactory` that serves the same stubs from a plain-JSON REST API | client apps migrating a backend to urpc (KMP) |
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

## 4. Migrating an existing REST backend

Adopting urpc usually means a client and a server that have to change together, which is the
expensive part. `urpc-client-rest` removes that constraint: write the contract and the client
against urpc now, and let the *existing* REST API serve it until the backend catches up.

```kotlin
implementation("dev.isaacudy.udytils:urpc-client-rest:<version>")
```

```kotlin
val urpc = httpClient.restUrpcClient(
    baseUrl = "https://api.example.com",
    // Anything the table below doesn't cover goes to the real urpc endpoint.
    fallback = httpClient.urpcClient("https://api.example.com"),
) {
    interceptors(bearerTokenInterceptor(authTokenFlow))
    tokenRefresher { auth.refresh() }
    service("userService") {                     // the @Urpc("userService") prefix
        headers("X-Api-Version" to "2")
        "getUser"    via get("/users/{id}")
        "listUsers"  via get("/users")
        "createUser" via post("/users")
        "updateUser" via put("/users/{id}")
        "deleteUser" via delete("/users/{id}")
        "search"     via post("/search") { response { it.jsonObject.getValue("data") } }
        "exportReport" via custom<ExportRequest, ExportResponse> { request -> /* full control */ }
    }
}
val users: UserService = urpc.create()          // the same generated stub, over REST
```

A call is routed by its wire name (`"userService.getUser"`). The request is encoded to JSON, then
`{field}` placeholders are filled from the matching request fields and consumed; whatever is left
becomes query parameters on `GET`/`DELETE` (repeating the name for a list, omitting nulls) or the
JSON body on `POST`/`PUT`/`PATCH`. A 2xx body goes through the route's optional `response { }`
transform and is decoded with the contract's serializer. Anything that doesn't fit — multipart, an
envelope a transform can't reach, two calls behind one function — is what `custom { }` is for.

Behaviour worth knowing:

- **Parity with the native client is deliberate.** Interceptors run before every call and their
  metadata becomes request headers; a 401 triggers the token refresher and exactly one retry with
  the chain re-run; failures decode to `ServiceException` by default (so an API already speaking
  `ServiceError` needs no configuration). Pass the same interceptor list to both clients and auth,
  tracing and error handling behave identically on either side of the migration.
- **Streaming has no REST equivalent.** Unmapped streaming and bidirectional calls go to the
  fallback; a streaming wire name mapped to a REST route throws `UnsupportedOperationException`.
- **Mistakes fail early.** A duplicated wire name or a malformed path template fails when the
  table is built, at startup. Everything the table can't express at call time — a placeholder
  naming a field the request hasn't got, a nested object in a query string — throws
  `RestUrpcException` naming both the wire name and the route.

### The migration itself

Move endpoints one at a time: when `userService.getUser` starts being served by the urpc server,
delete its line from the table and the call falls through to `fallback`. Nothing else changes —
not the contract, not the generated stub, not the call site. When the last line is gone, drop the
REST client and this dependency.

`RestUrpcClientFactory.mappedWireNames` reports what the table still covers, so a test can prove
the two halves add up rather than leaving a gap to be discovered as a 404 in production:

```kotlin
@Test fun everyUserServiceCallIsServedBySomething() {
    val declared = setOf("userService.getUser", "userService.listUsers", /* … */)
    val migrated = setOf("userService.listUsers")   // now served natively by the urpc server
    assertEquals(declared - migrated, buildRestClient().mappedWireNames)
}
```

The generated server binding knows the other half of that set exactly — `XServiceUrpcBinding`
matches calls against a `HANDLED_WIRE_NAMES` set built from the contract — but it keeps it
private, so `declared` above is a hand-written list for now. Keeping it beside the route table
is enough: adding a function to the contract without mapping or migrating it fails the test.

## Example

[`urpc/sample`](sample) is a complete contract module; its round-trip tests
(`ExampleServiceRoundTripTest`, `ExampleServiceWithKoinTest`) run every call shape —
unary, streaming, bidirectional, wire renames, typed stream errors, and multiplexed
failure isolation — through Ktor's test host and are the best end-to-end reference.
