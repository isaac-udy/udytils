@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.dsl.androidLibrary
import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlinKsp)
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
version = "1.0.0"

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

    listOf(
        iosX64(),
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
                implementation(compose.components.resources)
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

dependencies {
    add("kspCommonMainMetadata", libs.enro.processor)
    add("kspJvm", libs.enro.processor)
    add("kspAndroid", libs.enro.processor)
    add("kspIosX64", libs.enro.processor)
    add("kspIosArm64", libs.enro.processor)
    add("kspIosSimulatorArm64", libs.enro.processor)
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates("dev.isaacudy.udytils", "core", "1.0.0")

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
