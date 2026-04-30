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
include(":samples")
include(":ui")
// The nested-module simple name "core" collides with the top-level :core project,
// which causes a `compileKotlinJvm` -> `jvmJar` self-reference cycle when
// `:urpc:core` declares a dependency on `:core`. Rename the simple names to avoid
// the collision while keeping the nested directory layout. Note that this changes
// the project paths to `:urpc:urpc-core`, `:urpc:urpc-client`, `:urpc:urpc-server`.
include(":urpc:core")
project(":urpc:core").projectDir = file("urpc/core")
project(":urpc:core").name = "urpc-core"

include(":urpc:client")
project(":urpc:client").projectDir = file("urpc/client")
project(":urpc:client").name = "urpc-client"

include(":urpc:server")
project(":urpc:server").projectDir = file("urpc/server")
project(":urpc:server").name = "urpc-server"

include(":urpc:processor")
project(":urpc:processor").projectDir = file("urpc/processor")
project(":urpc:processor").name = "urpc-processor"

include(":urpc:sample")
project(":urpc:sample").projectDir = file("urpc/sample")
project(":urpc:sample").name = "urpc-sample"

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
