import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The Paparazzi snapshot-testing harness.
 *
 * Unlike :core and :ui this is a plain `com.android.library` rather than a KMP module. Paparazzi
 * renders through layoutlib against the Android Compose artifacts and only ever runs from an
 * Android host-test source set, so there is no second platform to target — a KMP wrapper with a
 * single android target would be ceremony with no consumer benefit. It also keeps the module's
 * tests on the standard `test` task, which is what CI already runs.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    id("udytils.publish")
}

android {
    namespace = "dev.isaacudy.udytils.snapshot"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Consumers write `@Preview`-driven or hand-written snapshot tests against these types, so
    // they resolve transitively from the artifact rather than being re-declared per module.
    api(libs.composablePreviewScanner.android)
    // JUnit 4 is the harness's runtime: PreviewSnapshotTestCase carries @RunWith/@Rule/@Test and
    // consumers write the @Parameterized.Parameters method themselves.
    api(libs.junit4)

    // Paparazzi is deliberately compileOnly. The consuming module applies the Paparazzi *Gradle
    // plugin*, which is what puts the runtime — and the matching layoutlib native artifacts and
    // `paparazzi.*` system properties — on the host-test classpath. Publishing our own version as
    // an `api` dependency would let the runtime drift from the plugin that drives it, which fails
    // in confusing ways (layoutlib/native mismatch) rather than at dependency resolution.
    compileOnly(libs.paparazzi)

    // Compose is compileOnly for the same reason: a snapshot test renders the *consumer's* Compose,
    // and the consumer necessarily has it (they are snapshotting Compose UI). Declaring it `api`
    // would pin their host-test classpath to whatever Compose this module was built against,
    // which can silently differ from the Compose their app actually ships.
    compileOnly(compose.runtime)
    compileOnly(compose.ui)
    compileOnly(compose.foundation)

    // The handler's own tests need the real Paparazzi types (Snapshot/SnapshotHandler); they never
    // render, so no layoutlib and no Paparazzi Gradle plugin is required here.
    testImplementation(libs.paparazzi)
    testImplementation(libs.junit4)
    // The Compose compiler plugin runs over every Kotlin compilation in the module, including the
    // unit tests, and refuses to compile without the runtime on the class path. The `compileOnly`
    // declaration above doesn't reach the test compilation, so restate it here.
    testImplementation(compose.runtime)
}

mavenPublishing {
    pom {
        name.set("Udytils Snapshot")
        description.set("Paparazzi snapshot-testing harness: preview-driven tests with directory-grouped goldens")
        inceptionYear.set("2026")
    }
}
