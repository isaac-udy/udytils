@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    id("udytils.publish")
    alias(libs.plugins.kotlinKsp)
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
        namespace = "$group.ui"
        minSdk = libs.versions.android.minSdk.get().toInt()
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "UdytilsUi"
            isStatic = true
        }
    }

    wasmJs {
        browser()
    }

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
                implementation(libs.androidx.activity.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
            }
        }

        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(libs.compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.io)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.enro.core)

                implementation(libs.udytils.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.enro.processor)
    add("kspJvm", libs.enro.processor)
    // Use the eagerly-created `kspAndroid` bucket (not `kspAndroidMain`): KSP maps it into the
    // lazily-created `kspAndroidMain` configuration, which doesn't yet exist when this
    // dependencies block is evaluated.
    add("kspAndroid", libs.enro.processor)
    add("kspWasmJs", libs.enro.processor)
    add("kspIosArm64", libs.enro.processor)
    add("kspIosSimulatorArm64", libs.enro.processor)
}

mavenPublishing {
    pom {
        name.set("Udytils UI")
        description.set("UI utilities for Kotlin Multiplatform development")
        inceptionYear.set("2025")
    }
}
