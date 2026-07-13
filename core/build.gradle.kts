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
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "$group.core"
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
//    js("js") {
//        nodejs()
//    }

    compilerOptions {
        // Apply options globally
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi"
        )
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.appcompat)
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.io)
                compileOnly(libs.compose.components.resources)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils Core")
        description.set("Core utilities for Kotlin Multiplatform development")
        inceptionYear.set("2025")
    }
}
