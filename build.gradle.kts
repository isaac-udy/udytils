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

            // Maven coords stay as `urpc-core`/`urpc-client`/`urpc-server`; project paths
            // are `:urpc:urpc-core` etc. due to the simple-name collision workaround in
            // settings.gradle.kts.
            substitute(module("dev.isaacudy.udytils:urpc-core"))
                .using(project(":urpc:urpc-core"))

            substitute(module("dev.isaacudy.udytils:urpc-client"))
                .using(project(":urpc:urpc-client"))

            substitute(module("dev.isaacudy.udytils:urpc-server"))
                .using(project(":urpc:urpc-server"))

            substitute(module("dev.isaacudy.udytils:urpc-processor"))
                .using(project(":urpc:urpc-processor"))
        }
    }
}
