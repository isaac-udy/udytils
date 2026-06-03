plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // The sample has no consuming Xcode project, so no framework binary is produced — these
    // targets exist to keep the shared sample code compiling for iOS. Add an
    // `iosTarget.binaries.framework { }` here if an iOS host app is introduced later.
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi"
        )
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":samples:common"))
            implementation(compose.runtime)
            implementation(compose.ui)
        }
    }
}
