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
        )
    }
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)

    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    pom {
        name.set("Udytils urpc - Processor")
        description.set("KSP processor that emits client and server bindings for @UrpcService interfaces")
        inceptionYear.set("2026")
    }
}
