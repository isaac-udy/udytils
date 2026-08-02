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
        namespace = "$group.urpc.client.rest"
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
                // Deliberately NOT :urpc:client — the fallback is passed in as a plain
                // UrpcClientFactory, so a consumer that has fully migrated off REST can drop
                // this module without ever having compiled against the native transport.
                api(project(":urpc:protocol"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization)
                implementation(libs.ktor.client.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils urpc - REST compatibility client")
        description.set(
            "UrpcClientFactory that serves generated urpc stubs from a plain-JSON REST API, " +
                "for incremental migration to native urpc",
        )
        inceptionYear.set("2026")
    }
}
