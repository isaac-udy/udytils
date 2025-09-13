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
