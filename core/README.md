# Udytils Core

Foundational Kotlin Multiplatform utilities: async state modelling, coroutine/flow
helpers, presentable errors, and a small file-cache layer. This is the base dependency
of the other udytils modules.

**Artifact:** `dev.isaacudy.udytils:core` · **Targets:** JVM, Android, iOS, wasmJs

```kotlin
dependencies {
    implementation("dev.isaacudy.udytils:core:<version>")
}
```

## `AsyncState<T>` — async operations as data

A sealed model of an asynchronous operation: `Idle` → `Loading(progress)` →
`Success(data)` / `Error(throwable)`. `Loading` carries an optional 0–1 progress value
(`isIndeterminate` when null); `Error` refuses to wrap `CancellationException`, so
cancellation always propagates instead of rendering as a failure.

```kotlin
var user by mutableStateOf<AsyncState<User>>(AsyncState.Idle())

suspend fun load() {
    AsyncState.fromSuspending { api.fetchUser() }
        .collect { user = it }
}
```

- **Builders:** `AsyncState.fromSuspending { }`, `AsyncState.fromFlow(flow)`,
  `AsyncState.fromDeferred(deferred)`, `Flow<T>.asAsyncState()` — each emits `Loading`
  then `Success`/`Error`.
- **Operators:** `map`, `mapEach` (for list states), `getOrNull`, `getOrThrow`,
  null-handling helpers, and `AsyncState.presentableError(title, message)` for
  user-facing failures.

## State containers

- **`UpdatableState<T>`** — an `Empty`/`Data` container for values that may not have
  loaded yet, with `updateFrom`, `dataOrNull`, `dataOrEmpty`, `map`, plus `fromFlow` /
  `fromSuspending` factories.
- **`RepositoryState<Repository, T>`** — a `StateFlow` whose `update` is only callable
  with the owning repository in context (via context parameters), so only the repository
  that owns the state can mutate it while everyone else just observes.

(For Compose `ViewModel` state, see [`ViewModelState`](../ui/README.md) in the ui module.)

## Coroutine and Flow utilities

- **`FlowCache<Key, T>`** — a keyed cache of shared flows with configurable replay and
  subscription timeouts; use it to share one upstream (e.g. a network observation) among
  many collectors.
- **`JobManager`** — launch coroutines under string keys with a `JOIN` (reuse the running
  job) or `REPLACE` (cancel and restart) strategy.
- **`RefreshableJob`** — a job wrapper that can be `refresh()`ed and cancelled as one unit.
- **`Flow.retryTransient(initialDelay, maxDelay)`** — retry upstream failures that are
  retryable (`Throwable.isRetryable()`) with capped exponential backoff.
- **`Flow.asRetryableSharedFlow(scope)`** — share a flow whose failures can be retried by
  collectors via a monotonic retry signal (value-equal failures are not deduplicated).
- **`withTimeBounds(minimumBound, maximumBound) { }`** — smooths loading UI: work that
  finishes inside the min–max window is padded to `maximumBound` so indicators don't
  flash; work outside the window returns immediately.

## Presentable errors

`PresentableException` is a serializable exception carrying a user-facing `ErrorMessage`
(title, message, retryable flag, and a generated error ID for support/debugging):

```kotlin
throw presentableException(
    title = "Sync failed",
    message = "Your changes are saved locally and will sync when you're back online.",
    retryable = true,
)
```

`Throwable.getErrorMessage()` converts any exception into an `ErrorMessage` (using the
presentable data when available, a generic fallback otherwise), and
`Throwable.isRetryable()` reports whether retrying is worthwhile. The ui module's error
dialog renders these directly.

## File utilities

`FileReference` / `DirectoryReference` / `FileData` wrap `kotlinx-io` paths, and
`FileCache<T>` persists a serializable value to disk as JSON with an in-memory layer on
top — `FileCacheProvider.cache()` is the usual entry point.

## Odds and ends

- `StringOrResource` — holds either a literal string or a Compose `StringResource`,
  resolved at display time (the ui module adds `@Composable asString()`).
- `Any?.requireNotNull()`, `EnumEntries.valueOrDefault(name, default)`,
  `Double/Float.toDecimalString(decimals)`.
