pluginManagement {
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
