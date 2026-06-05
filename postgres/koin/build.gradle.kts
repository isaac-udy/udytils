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
    api(rootProject.project(":postgres-core"))
    api(libs.koin.core)

    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.isaacudy.udytils", "postgres-koin", versionName)

    pom {
        name.set("Udytils Postgres - Koin integration")
        description.set("Koin module wiring the udytils Postgres DataSource, Exposed Database, migrator and notification bus.")
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
