pluginManagement {
    // Convention plugins (e.g. udytils.publish) live in the build-logic included build.
    includeBuild("build-logic")
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

include(":core")
include(":ui")
// The sample app is split per-platform: a shared KMP library plus thin app modules.
// AGP 9.0 no longer allows a single KMP module to also be a `com.android.application`,
// so the Android launcher is its own module that consumes :samples:common.
include(":samples:common")
include(":samples:app:android")
include(":samples:app:desktop")
include(":samples:app:ios")
// urpc RPC framework modules. The protocol module is named "protocol" (directory
// urpc/protocol) so its simple name no longer collides with the top-level :core
// project — that collision previously forced a `.name` workaround and caused a
// `compileKotlinJvm` -> `jvmJar` self-reference cycle when it depended on :core.
include(":urpc:protocol")
include(":urpc:client")
include(":urpc:server")
include(":urpc:processor")
include(":urpc:sample")
include(":urpc:koin")

// Postgres database-management toolkit: a runtime library, an optional Koin
// add-on, a build-only codegen engine, and the Gradle plugin that wires the
// codegen into a consumer build.
// Flat, unique project names (matching the published artifact names) so the
// leaf "koin" doesn't collide with :urpc:koin in the composite build — a
// simple-name collision silently misroutes dependency substitution.
include(":postgres-core")
include(":postgres-koin")
include(":postgres-codegen")
include(":postgres-gradle-plugin")
include(":postgres-embedded")
project(":postgres-core").projectDir = file("postgres/core")
project(":postgres-koin").projectDir = file("postgres/koin")
project(":postgres-codegen").projectDir = file("postgres/codegen")
project(":postgres-gradle-plugin").projectDir = file("postgres/gradle-plugin")
project(":postgres-embedded").projectDir = file("postgres/embedded")

// Architecture-as-code framework: the rule/doc engine (JVM, Konsist-based) and the
// exemption annotation (multiplatform, so any governed module can carry exemptions).
// Flat, unique project names matching the published artifact names — see the postgres
// note above about simple-name collisions in composite builds.
include(":architecture-core")
include(":architecture-annotations")
include(":architecture-gradle-plugin")
project(":architecture-core").projectDir = file("architecture/core")
project(":architecture-annotations").projectDir = file("architecture/annotations")
project(":architecture-gradle-plugin").projectDir = file("architecture/gradle-plugin")

// The repo's own architecture catalog (not published) — udytils dogfooding its architecture
// framework on itself. Hand-wired rather than applying the :architecture-gradle-plugin,
// because a build can't apply a plugin produced by one of its own subprojects.
include(":udytils-architecture")
project(":udytils-architecture").projectDir = file("architecture/udytils")

// When embedded-udytils is used as an included build alongside embedded-enro, the Kotlin
// wasmJs plugin's wasmRootPackageJson task needs to resolve embedded-enro as an included
// build. This conditional include makes that work without breaking standalone usage.
val enroDir = file("../embedded-enro")
if (enroDir.exists()) {
    includeBuild("../embedded-enro") {
        name = "embedded-enro"
        dependencySubstitution {
            substitute(module("dev.enro:enro-processor")).using(project(":enro-processor"))
            substitute(module("dev.enro:enro-annotations")).using(project(":enro-annotations"))
            substitute(module("dev.enro:enro-test")).using(project(":enro-test"))
            substitute(module("dev.enro:enro-lint")).using(project(":enro-lint"))
            substitute(module("dev.enro:enro")).using(project(":enro"))
            substitute(module("dev.enro:enro-common")).using(project(":enro-common"))
            substitute(module("dev.enro:enro-runtime")).using(project(":enro-runtime"))
            substitute(module("dev.enro:enro-compat")).using(project(":enro-compat"))
            substitute(module("dev.enro:tests:application")).using(project(":tests:application"))
            substitute(module("dev.enro:tests:module-one")).using(project(":tests:module-one"))
        }
    }
}
