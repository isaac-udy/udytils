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
    }
}

dependencies {
    api(libs.kotlinx.html)
    api(libs.ktor.server.core)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.jsoup)
}

mavenPublishing {
    pom {
        name.set("Udytils htmx")
        description.set("Typed htmx, htmx-sse and Alpine CSP attributes for kotlinx.html, Ktor request and response helpers, out-of-band list updates, and bundled htmx and Alpine builds.")
        inceptionYear.set("2026")
    }
}
