// Compose HTML destinations for the ui module's navigation keys. Kotlin/JS only, and no Compose UI
// code: an app that renders with Compose HTML depends on this module instead of rendering ui's
// Compose UI destinations.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Unpacks skiko's runtime into the jsTest bundle: development builds load all of :ui, Compose UI
    // included, even though nothing here calls it.
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    id("udytils.publish")
    alias(libs.plugins.kotlinKsp)
}

kotlin {
    js {
        browser()
    }

    compilerOptions {
        // Match :ui, whose output these preview features mark pre-release.
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        jsMain.dependencies {
            api(project(":ui"))
            api(libs.udytils.core)
            api(libs.compose.runtime)
            api(libs.compose.html.core)
            api(libs.enro.html)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.compose.html.testUtils)
            implementation(libs.kotlinx.serialization)
        }
    }
}

dependencies {
    add("kspJs", libs.enro.processor)
}

mavenPublishing {
    pom {
        name.set("Udytils UI HTML")
        description.set("Compose HTML destinations for the confirmation and error dialogs in Udytils UI")
        inceptionYear.set("2026")
    }
}
