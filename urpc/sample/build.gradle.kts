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
    // The contract module ONLY depends on urpc-core. Generated KSP code references
    // UrpcClientFactory / UrpcRoute / ServiceDescriptor — all defined in core.
    // Clients and servers add the Ktor-backed implementations as their own deps.
    implementation(rootProject.project(":urpc:urpc-core"))
    ksp(rootProject.project(":urpc:urpc-processor"))

    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.core)

    // Test source set wires up the concrete implementations to round-trip a real
    // request through Ktor's testApplication.
    testImplementation(rootProject.project(":urpc:urpc-client"))
    testImplementation(rootProject.project(":urpc:urpc-server"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}
