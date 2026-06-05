import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "dev.isaacudy.udytils"
val versionName = libs.versions.udytilsVersionName.get()
version = versionName

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
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.isaacudy.udytils", "postgres-core", versionName)

    pom {
        name.set("Udytils Postgres Core")
        description.set("Runtime helpers for Postgres + Exposed: custom column types, a Flyway migrator, a LISTEN/NOTIFY bus, and connection config.")
        inceptionYear.set("2026")
        url.set("https://github.com/isaacudy/udytils")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("isaacudy")
                name.set("Isaac Udy")
                url.set("https://github.com/isaacudy")
            }
        }
        scm {
            url.set("https://github.com/isaacudy/udytils")
            connection.set("scm:git:git://github.com/isaacudy/udytils.git")
            developerConnection.set("scm:git:ssh://git@github.com/isaacudy/udytils.git")
        }
    }
}
