@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.android.library)
}

group = "dev.isaacudy.udytils"
val versionName = libs.versions.udytilsVersionName.get()
version = versionName

kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
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
                api(project(":urpc:urpc-core"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
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

extensions.configure<BaseExtension> {
    namespace = "$group.urpc.client"
    compileSdkVersion(libs.versions.android.compileSdk.get().toInt())
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.isaacudy.udytils", "urpc-client", versionName)

    pom {
        name.set("Udytils urpc - Client")
        description.set("Ktor-based client for the udytils RPC framework")
        inceptionYear.set("2026")
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
