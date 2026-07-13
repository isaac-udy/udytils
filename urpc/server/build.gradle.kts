import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    id("udytils.publish")
    alias(libs.plugins.kotlinSerialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi"
        )
    }
}

dependencies {
    api(rootProject.project(":urpc:protocol"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.websockets)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    pom {
        name.set("Udytils urpc - Server")
        description.set("Ktor-based server bindings for the udytils RPC framework")
        inceptionYear.set("2026")
    }
}
