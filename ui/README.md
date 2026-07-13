# Udytils UI

Compose Multiplatform UI utilities built on top of [`core`](../core/README.md) and
[Enro](https://github.com/isaac-udy/Enro) navigation: a ViewModel state holder, reusable
components, error and confirmation dialog destinations, and a permissions abstraction.

**Artifact:** `dev.isaacudy.udytils:ui` · **Targets:** JVM, Android, iOS, wasmJs

```kotlin
dependencies {
    implementation("dev.isaacudy.udytils:ui:<version>")
}
```

> The destination-based features (error dialogs, confirmations, permission requests)
> are Enro `@NavigationDestination`s — they assume your app already uses Enro for
> navigation. The components and `ViewModelState` have no navigation dependency.

## `ViewModelState<T>`

A state holder for Compose `ViewModel`s: mutation is only possible with the owning
ViewModel in context (context parameters), while the UI just collects.

```kotlin
class ProfileViewModel : ViewModel() {
    val state = viewModelState<AsyncState<Profile>>(AsyncState.Idle())

    fun load() = viewModelScope.launch {
        AsyncState.fromSuspending { repository.profile() }
            .collect { state.update(it) }   // update() needs the ViewModel context
    }
}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val profile by viewModel.state.collectAsState()
    // render AsyncState...
}
```

## Components

Small, theme-respecting building blocks: `ContentCard`, `ListCard`, `EmptyContent`
(empty-state placeholder), and the text styles `HeadlineText`, `BodyText`, `LabelText`.
Plus the modifiers `Modifier.animateAlphaOnEnter()` and `Modifier.disablePointerInput()`.

## Error handling

- **`ErrorDialogDestination`** — an Enro dialog destination that renders any
  `Throwable` via `Throwable.getErrorMessage()` from core, with retry support for
  retryable errors.
- **`ViewModel.registerErrorHandler { }`** / **`ErrorHandler<T>`** — route errors from
  ViewModel operations into UI handling in one place.

## Confirmation flows

`ConfirmationDestination` (with `defaultConfirmationDestination`) and
`floatingCardDestination<T>()` provide ready-made "are you sure?" dialogs and floating
card presentation for your own destinations.

## Permissions

`Permission` (e.g. location permissions), `PermissionStatus`, `hasPermission()`,
`@Composable rememberHasPermission(...)` and `RequestPermissionDestination` model
permission checks and requests as data + navigation.

> **Platform support:** Android and iOS are implemented. The desktop JVM and wasmJs
> `actual`s are currently `TODO()` stubs and **throw at runtime** — don't call the
> permissions API on those targets yet.

## Android extras

`androidMain` ships `Udytils` (init hooks), `ActivityObserver`,
`activityResultDestination` and `ApplicationSettingsDestination` (deep-link into the
app's system settings screen).
