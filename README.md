# udytils

A collection of Kotlin Multiplatform utilities designed to streamline common development patterns
across Android, iOS, JVM, and WASM targets.

## Features

### 🔄 Async State Management

- **`AsyncState<T>`**: Type-safe representation of asynchronous operations with `Idle`, `Loading`,
  `Success`, and `Error` states
- Comprehensive extension functions for state transformation and handling
- Flow integration for reactive programming
- Null-safe mapping utilities

### 🎯 Coroutine Utilities

- **`JobManager`**: Smart coroutine job management with strategies for joining or replacing existing
  jobs
- **`RefreshableJob`**: Refreshable coroutine jobs with cancellation support
- **`withTimeBounds`**: Time-bounded coroutine execution

### 📦 State Management

- **`UpdatableState<T>`**: Mutable state containers with async operations
- **`ViewModelState`**: ViewModel-ready state management
- **`RepositoryState`**: Repository pattern state management
- Factory functions for creating states from Flows and suspending functions

### ⚠️ Error Handling

- **`PresentableException`**: User-friendly error representations
- **`ErrorMessage`**: Structured error messages with retry capabilities
- Automatic error ID generation for debugging

### 🔌 urpc — typed RPC over Ktor

A small RPC framework built on Ktor for defining typed request/response and streaming
service calls between a KMP client and a JVM Ktor server.

- **`urpc-core`**: protocol-only types — `ServiceFunction`, `StreamingServiceFunction`,
  `BidirectionalStreamingServiceFunction`, `ServiceError`, `ServiceException`
- **`urpc-client`**: KMP `ServiceClientFactory` that builds typed callers backed by
  Ktor HTTP and WebSocket transports, with auto-reconnect and 401-triggered token
  refresh
- **`urpc-server`**: JVM Ktor bindings + `Route.urpc(resolver)` for mounting the
  HTTP and WebSocket routes
