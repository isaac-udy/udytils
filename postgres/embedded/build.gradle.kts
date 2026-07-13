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
    api(libs.zonky.embeddedPostgres)
    // Zonky needs a native Postgres binary for the running OS/arch; ship all the
    // common ones so the helper works on dev Macs + Linux CI without extra setup.
    runtimeOnly(libs.zonky.postgresBinaries.darwinArm64)
    runtimeOnly(libs.zonky.postgresBinaries.darwinAmd64)
    runtimeOnly(libs.zonky.postgresBinaries.linuxAmd64)
    runtimeOnly(libs.zonky.postgresBinaries.linuxArm64)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    pom {
        name.set("Udytils Postgres - Embedded dev helper")
        description.set("Starts an in-process Zonky embedded Postgres and exposes a ready-to-use PostgresConfig for local dev / tests.")
        inceptionYear.set("2026")
    }
}
