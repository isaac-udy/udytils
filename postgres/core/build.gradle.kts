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
        // kotlin.time.Instant (used by TimestampColumnType) is still experimental.
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

dependencies {
    // Re-export the Exposed API so consumers can declare tables / run
    // transactions without re-declaring it.
    api(libs.exposed.core)
    api(libs.exposed.jdbc)

    // Compile scope, NOT runtimeOnly: JsonbColumnType references PGobject and
    // PgNotificationBus references PGConnection/PgConnection directly.
    implementation(libs.postgresql)
    // api: buildHikariDataSource() returns a HikariDataSource, so the type is
    // part of this module's public surface (and the pool is AutoCloseable).
    api(libs.hikaricp)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.kotlinx.coroutines.core)

    // Logging seam. No binding is shipped — the consumer binds (e.g. logback);
    // without a binding SLF4J falls back to NOP and these logs go silent.
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    pom {
        name.set("Udytils Postgres Core")
        description.set("Runtime helpers for Postgres + Exposed: custom column types, a Flyway migrator, a LISTEN/NOTIFY bus, and connection config.")
        inceptionYear.set("2026")
    }
}
