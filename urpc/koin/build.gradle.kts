import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    id("udytils.publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi",
        )
    }
}

dependencies {
    api(rootProject.project(":urpc:protocol"))
    api(rootProject.project(":urpc:server"))
    api(libs.koin.core)
    api(libs.koin.ktor)
    implementation(libs.ktor.server.core)

    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    pom {
        name.set("Udytils urpc - Koin integration")
        description.set("Koin integration helpers for the udytils RPC framework")
        inceptionYear.set("2026")
    }
}
