@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    id("udytils.publish")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "dev.isaacudy.udytils.trace.otlp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        // Karma cannot load Ktor's wasm client ("Cannot use 'import.meta' outside a module"); the tests run on Node.
        browser { testTask { enabled = false } }
        nodejs()
    }
    js { browser() }

    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            api(project(":trace-runtime"))
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils Trace - OTLP")
        description.set("Exports trace spans to an OpenTelemetry collector over OTLP/HTTP JSON.")
        inceptionYear.set("2026")
    }
}
