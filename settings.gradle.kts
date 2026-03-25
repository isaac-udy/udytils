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
