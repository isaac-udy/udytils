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
    // HtmlScrubber exposes jsoup's Element.
    api(libs.jsoup)
    implementation(libs.kotlin.test)

    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    pom {
        name.set("Udytils HTML Snapshot")
        description.set("Golden-file assertions for rendered HTML, normalised with jsoup so goldens change only when the markup does.")
        inceptionYear.set("2026")
    }
}
