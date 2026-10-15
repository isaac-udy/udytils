# Udytils UI

Compose Multiplatform UI utilities built on top of [`core`](../core/README.md) and
[Enro](https://github.com/isaac-udy/Enro) navigation: a ViewModel state holder, reusable
components, error and confirmation dialog destinations, and a permissions abstraction.

**Artifact:** `dev.isaacudy.udytils:ui` · **Targets:** JVM, Android, iOS, wasmJs, js

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

`ViewModelState` is declared in the `dev.isaacudy.udytils:viewmodel` artifact, which has no
Compose UI dependency, so renderers other than Compose UI can use it. `ui` re-exports it.

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

## Compose HTML

`dev.isaacudy.udytils:ui-html` (js only) renders `ConfirmationDestination` and
`ErrorDialogDestination` with Compose HTML, as modal `<dialog>` elements, for an app that renders
with `enro-html` instead of Compose UI. Its destinations are
`@NavigationDestination.PlatformOverride`s, so Enro's code generator registers them in place of
this module's Compose UI destinations.

```kotlin
// build.gradle.kts, the Compose HTML app
kotlin {
    sourceSets {
        jsMain.dependencies {
            implementation("dev.isaacudy.udytils:ui-html:<version>")
        }
    }
}
```

- **Classes:** each element carries `udytils-*` classes. Provide your design system's classes
  through `LocalUdytilsHtmlClasses`.
- **Text:** Compose HTML can't read Compose resources. A `StringOrResource` holding a resource
  key reads its text from `LocalHtmlStringResolver`; the `confirm` and `close` defaults fall back
  to English. A key with no text throws.

## Permissions

`Permission` (e.g. location permissions), `PermissionStatus`, `hasPermission()`,
`@Composable rememberHasPermission(...)` and `RequestPermissionDestination` model
permission checks and requests as data + navigation.

> **Platform support:** Android and iOS are implemented. The desktop JVM, wasmJs and js
> `actual`s are currently `TODO()` stubs and **throw at runtime** — don't call the
> permissions API on those targets yet.

## Android extras

`androidMain` ships `Udytils` (init hooks), `ActivityObserver`,
`activityResultDestination` and `ApplicationSettingsDestination` (deep-link into the
app's system settings screen).
