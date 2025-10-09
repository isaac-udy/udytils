plugins {
    alias(libs.plugins.android.kotlinMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
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
        }
    }
}
