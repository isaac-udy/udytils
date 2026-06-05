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
    }
}

dependencies {
    // Build-only codegen engine: boots an embedded Postgres, applies Flyway
    // migrations, introspects pg_catalog, and emits SQL/Kotlin source as TEXT.
    // It does NOT link Exposed — it only writes Exposed source.
    api(libs.zonky.embeddedPostgres)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    implementation(libs.postgresql)
    // Build-time logging for Zonky/Flyway only.
    runtimeOnly(libs.logback)

    // Zonky needs a native Postgres binary for the running OS/arch. The Gradle
    // plugin adds these to the codegen worker classpath (default: all four) so
    // they can be trimmed per project; tests pull all four so they run on any
    // dev machine or CI runner.
    testImplementation(libs.zonky.postgresBinaries.darwinArm64)
    testImplementation(libs.zonky.postgresBinaries.darwinAmd64)
    testImplementation(libs.zonky.postgresBinaries.linuxAmd64)
    testImplementation(libs.zonky.postgresBinaries.linuxArm64)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.isaacudy.udytils", "postgres-codegen", versionName)

    pom {
        name.set("Udytils Postgres - Codegen engine")
        description.set("Build-time engine: applies Flyway migrations to an embedded Postgres, snapshots the schema, and generates Exposed Table/Row sources.")
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
