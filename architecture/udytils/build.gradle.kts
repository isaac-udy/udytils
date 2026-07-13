import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// udytils' own architecture catalog. This module hand-wires what the
// dev.isaacudy.udytils.architecture Gradle plugin would otherwise set up (an architectureTest
// source set + generated test classes), because a build cannot apply a plugin produced by one
// of its own subprojects. The catalog lives in `main`; the equivalent of the generated tests
// is written by hand in `test` (see src/test/kotlin).
plugins {
    alias(libs.plugins.kotlinJvm)
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
    // Substituted to the local :architecture-core project by the root dependencySubstitution.
    implementation("dev.isaacudy.udytils:architecture-core:${libs.versions.udytilsVersionName.get()}")
    testRuntimeOnly(libs.junit.jupiterEngine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Konsist scans the whole repository and the docs golden test compares committed files —
    // inputs Gradle can't track — so the suite must re-run every time it is asked for.
    outputs.upToDateWhen { false }
}
