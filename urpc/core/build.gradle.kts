@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
}

plugins.apply(libs.plugins.android.library.get().pluginId)

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
                // WORKAROUND: a bare `project(":core")` resolves to *this* project from
                // inside a nested module whose simple name also happens to be "core",
                // producing a circular task dependency. Resolve through `rootProject`
                // explicitly so we always pick up the top-level :core project.
                //
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

extensions.configure<BaseExtension> {
    namespace = "$group.urpc.core"
    compileSdkVersion(libs.versions.android.compileSdk.get().toInt())
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.isaacudy.udytils", "urpc-core", versionName)

    pom {
        name.set("Udytils urpc - Core")
        description.set("Protocol-only types for the udytils RPC framework, shared between client and server")
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
