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
    api(rootProject.project(":postgres-core"))
    api(libs.koin.core)

    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    pom {
        name.set("Udytils Postgres - Koin integration")
        description.set("Koin module wiring the udytils Postgres DataSource, Exposed Database, migrator and notification bus.")
        inceptionYear.set("2026")
    }
}
