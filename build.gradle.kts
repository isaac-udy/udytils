plugins {
    alias(libs.plugins.android.kotlinMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlinKsp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}


allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("dev.isaacudy.udytils:core"))
                .using(project(":core"))

            substitute(module("dev.isaacudy.udytils:ui"))
                .using(project(":ui"))

            substitute(module("dev.isaacudy.udytils:snapshot"))
                .using(project(":snapshot"))

            substitute(module("dev.isaacudy.udytils:urpc-protocol"))
                .using(project(":urpc:protocol"))

            substitute(module("dev.isaacudy.udytils:urpc-client"))
                .using(project(":urpc:client"))

            substitute(module("dev.isaacudy.udytils:urpc-server"))
                .using(project(":urpc:server"))

            substitute(module("dev.isaacudy.udytils:urpc-processor"))
                .using(project(":urpc:processor"))

            substitute(module("dev.isaacudy.udytils:urpc-koin"))
                .using(project(":urpc:koin"))

            substitute(module("dev.isaacudy.udytils:postgres-core"))
                .using(project(":postgres-core"))
            substitute(module("dev.isaacudy.udytils:postgres-koin"))
                .using(project(":postgres-koin"))
            substitute(module("dev.isaacudy.udytils:postgres-codegen"))
                .using(project(":postgres-codegen"))
            substitute(module("dev.isaacudy.udytils:postgres-gradle-plugin"))
                .using(project(":postgres-gradle-plugin"))
            substitute(module("dev.isaacudy.udytils:postgres-embedded"))
                .using(project(":postgres-embedded"))

            substitute(module("dev.isaacudy.udytils:architecture-core"))
                .using(project(":architecture-core"))
            substitute(module("dev.isaacudy.udytils:architecture-annotations"))
                .using(project(":architecture-annotations"))
            substitute(module("dev.isaacudy.udytils:architecture-gradle-plugin"))
                .using(project(":architecture-gradle-plugin"))
        }
    }
}
