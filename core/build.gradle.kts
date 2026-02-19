@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.dsl.androidLibrary
import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
}

val useMultiplatformAndroidLibrary = false
if (useMultiplatformAndroidLibrary) {
    plugins.apply(libs.plugins.android.kotlinMultiplatformLibrary.get().pluginId)
}
else {
    plugins.apply(libs.plugins.android.library.get().pluginId)
}

group = "dev.isaacudy.udytils"
val versionName = libs.versions.udytilsVersionName.get()
version = versionName

kotlin {
    jvm()

    if (useMultiplatformAndroidLibrary) {
        @Suppress("UnstableApiUsage")
        androidLibrary {
            namespace = "$group.core"
            minSdk = libs.versions.android.minSdk.get().toInt()
            compileSdk = libs.versions.android.compileSdk.get().toInt()
            withHostTestBuilder {}.configure {}
            withDeviceTestBuilder {
                sourceSetTreeName = "test"
            }
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
            experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
        }
    }
    else {
        androidTarget {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs("wasmJs") {
        browser()
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
                implementation(libs.enro.common)
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

if (!useMultiplatformAndroidLibrary) {
    extensions.configure<BaseExtension> {
        namespace = "$group.core"
        compileSdkVersion(libs.versions.android.compileSdk.get().toInt())
        defaultConfig {
            minSdk = libs.versions.android.minSdk.get().toInt()
        }
        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
        sourceSets["main"].res.srcDirs("src/androidMain/res")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.isaacudy.udytils", "core", versionName)

    pom {
        name.set("Udytils Core")
        description.set("Core utilities for Kotlin Multiplatform development")
        inceptionYear.set("2025")
        url.set("https://github.com/isaacudy/udytils")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("isaacudy")
                name.set("Isaac Udy")
                url.set("https://github.com/isaacudy")
            }
        }
        scm {
            url.set("https://github.com/isaacudy/udytils")
            connection.set("scm:git:git://github.com/isaacudy/udytils.git")
            developerConnection.set("scm:git:ssh://git@github.com/isaacudy/udytils.git")
        }
    }
}
