@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

// Compiles sample code with the trace compiler plugin on every target and checks the spans in commonTest.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosSimulatorArm64 {
        binaries.executable(listOf(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE)) { entryPoint = "sample.main" }
    }
    wasmJs { browser() }
    js { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":trace-runtime"))
            implementation(project(":core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    targets.configureEach {
        compilations.matching { it.name == KotlinCompilation.MAIN_COMPILATION_NAME && it.platformType.name != "common" }.configureEach {
            val compilation = this
            val manifestDir = layout.buildDirectory.dir("trace/manifest/${target.name}")
            project.dependencies.add("kotlinCompilerPluginClasspath" + target.name.replaceFirstChar(Char::uppercase) + compilation.name.replaceFirstChar(Char::uppercase), project(":trace-compiler-plugin"))
            compileTaskProvider.configure {
                outputs.dir(manifestDir)
                compilerOptions.freeCompilerArgs.addAll(
                    "-P", "plugin:dev.isaacudy.udytils.trace:manifestDir=${manifestDir.get().asFile.absolutePath}",
                    "-P", "plugin:dev.isaacudy.udytils.trace:include=sample",
                )
            }
        }
    }
}
