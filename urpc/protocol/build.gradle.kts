@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    id("udytils.publish")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "$group.urpc.protocol"
        minSdk = libs.versions.android.minSdk.get().toInt()
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosArm64()
    iosSimulatorArm64()
    wasmJs("wasmJs") {
        browser()
        compilerOptions {
            freeCompilerArgs.add("-Xwasm-kclass-fqn")
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi"
        )
    }

    sourceSets {
        commonMain {
            dependencies {
                // TODO(urpc): if :core later acquires runtime deps that aren't relevant
                // to error handling, revisit extracting ErrorMessage / PresentableException
                // into a small dedicated module so urpc consumers stay lean. Today :core
                // only brings in coroutines / serialization / datetime / io — all of
                // which urpc would need anyway — so the indirection isn't worth it yet.
                api(project(":core"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils urpc - Protocol")
        description.set("Protocol-only types for the udytils RPC framework, shared between client and server")
        inceptionYear.set("2026")
    }
}
