import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinKsp)
    alias(libs.plugins.kotlinSerialization)
}

// Shared sample code consumed by the per-platform app modules (:samples:app:android,
// :samples:app:desktop, :samples:app:ios). Under AGP 9.0 a Kotlin Multiplatform module can
// no longer also be a `com.android.application`, so the Android launcher lives in
// :samples:app:android and depends on this library's Android variant.
kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "dev.isaacudy.udytils.samples.common"
        minSdk = 23
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // Apply options globally
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization)
                // `api` so the app modules pick up Enro (incl. platform entry points used by
                // their main()/MainViewController) transitively from :samples:common.
                api(libs.enro.core)

                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.mikepenz.markdown.core)
                implementation(libs.mikepenz.markdown.m3)

                api(project(":core"))
                api(project(":ui"))
            }
        }
    }
}

compose.resources {
    // Pin the generated Res class package so it stays stable after the module move. The
    // default tracks the Gradle module path, which would now produce
    // `udytils.samples.common.generated.resources`; keeping the original package means the
    // shared sample code's `udytils.samples.generated.resources.*` imports still resolve.
    packageOfResClass = "udytils.samples.generated.resources"
}

dependencies {
    // No kspCommonMainMetadata pass: Enro's processor only supports concrete platforms (it
    // throws "Unsupported platform!" on the common-metadata target), and the per-target KSP
    // passes below already generate the bindings + installNavigationController for every
    // target from the shared commonMain sources, so the metadata pass is redundant.
    add("kspDesktop", libs.enro.processor)
    // `kspAndroid` (not `kspAndroidMain`): KSP maps this eagerly-created bucket into the
    // lazily-created `kspAndroidMain` configuration, which isn't available yet here.
    add("kspAndroid", libs.enro.processor)
    add("kspIosArm64", libs.enro.processor)
    add("kspIosSimulatorArm64", libs.enro.processor)
}
