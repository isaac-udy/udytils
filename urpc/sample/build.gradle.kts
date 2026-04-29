plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinKsp)
}

group = "dev.isaacudy.udytils"
val versionName = libs.versions.udytilsVersionName.get()
version = versionName

kotlin {
    compilerOptions {
        // Inherits the default jvm target from the toolchain — matches what
        // :urpc:urpc-client publishes (KMP jvm() target with no explicit override).
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi",
        )
    }
}

dependencies {
    implementation(rootProject.project(":urpc:urpc-core"))
    implementation(rootProject.project(":urpc:urpc-client"))
    implementation(rootProject.project(":urpc:urpc-server"))
    ksp(rootProject.project(":urpc:urpc-processor"))

    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.server.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}
