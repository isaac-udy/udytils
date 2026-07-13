# udytils

[![CI](https://github.com/isaac-udy/udytils/actions/workflows/ci.yml/badge.svg)](https://github.com/isaac-udy/udytils/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dev.isaacudy.udytils/core)](https://central.sonatype.com/search?q=g:dev.isaacudy.udytils)

A collection of Kotlin and Kotlin Multiplatform libraries extracted from real projects:
async state modelling, Compose UI helpers, a typed RPC framework, a Postgres + Exposed
toolkit, and an architecture-as-code testing framework.

These libraries are opinionated and built primarily for my own projects, but they are
documented, tested, and published for anyone to use. APIs may still move between minor
versions — pin your versions and read the release notes.

## Modules

| Family | Artifacts (`dev.isaacudy.udytils:*`) | Platforms | What it is |
|---|---|---|---|
| [core](core/README.md) | `core` | JVM, Android, iOS, wasmJs | Async state modelling (`AsyncState`), coroutine/flow utilities, error presentation, file caching |
| [ui](ui/README.md) | `ui` | JVM, Android, iOS, wasmJs | Compose Multiplatform components, `ViewModelState`, error dialogs, confirmation + permission flows (built on [Enro](https://github.com/isaac-udy/Enro)) |
| [urpc](urpc/README.md) | `urpc-protocol`, `urpc-client`, `urpc-server`, `urpc-processor`, `urpc-koin` | client: KMP · server: JVM | Typed RPC over Ktor: unary calls over HTTP, streaming + bidirectional calls multiplexed over one WebSocket, with KSP-generated bindings |
| [postgres](postgres/README.md) | `postgres-core`, `postgres-koin`, `postgres-codegen`, `postgres-embedded` + Gradle plugin `dev.isaacudy.udytils.postgres` | JVM | Postgres + Exposed toolkit: Flyway-driven schema codegen, custom column types, LISTEN/NOTIFY → Flow bus |
| [architecture](architecture/README.md) | `architecture-core`, `architecture-annotations` + Gradle plugin `dev.isaacudy.udytils.architecture` | core: JVM · annotations: KMP | Architecture-as-code: declare rules as Kotlin objects, get generated JUnit 5 tests and generated Markdown documentation |

## Installation

All artifacts are on Maven Central:

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.isaacudy.udytils:core:<version>")
}
```

The Gradle plugins (`dev.isaacudy.udytils.postgres`, `dev.isaacudy.udytils.architecture`)
are also resolved from Maven Central, which is not in Gradle's default plugin search, so
add it once in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

> The `postgres-*` and `architecture-*` artifacts ship from **v1.3.0**; earlier releases
> contain only `core`, `ui`, and the `urpc-*` artifacts.

## Samples

`samples/` contains a catalog app demonstrating the core and ui modules:

```
./gradlew :samples:app:desktop:desktopRun        # desktop
./gradlew :samples:app:android:installDebug      # android (device/emulator attached)
```

The urpc modules have a self-contained example in [`urpc/sample`](urpc/sample), which
defines an `@Urpc` service interface and round-trips real calls through Ktor's test host
— its tests are the best end-to-end reference for the framework.

## Development

```
./gradlew jvmTest test        # all JVM-side tests (this is what CI runs)
./gradlew iosSimulatorArm64Test   # iOS tests (macOS only)
```

CI builds every PR across JVM, Android, wasm and iOS. Issues and PRs are welcome — for
larger changes, open an issue first to check the direction fits the project's scope.

## License

```
Copyright 2025 Isaac Udy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
