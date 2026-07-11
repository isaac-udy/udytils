import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.mavenPublish)
    `java-gradle-plugin`
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
    // Model, store, and report rendering; the tasks are thin wrappers over these.
    implementation("dev.isaacudy.udytils:metrics-core:$versionName")
}

gradlePlugin {
    plugins {
        create("metrics") {
            id = "dev.isaacudy.udytils.metrics"
            implementationClass = "dev.isaacudy.udytils.metrics.gradle.MetricsPlugin"
            displayName = "Udytils Metrics"
            description = "Codebase health metrics: pluggable integrations, a git-branch store, and an HTML trend report."
        }
    }
}

mavenPublishing {
    configure(GradlePlugin(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.isaacudy.udytils", "metrics-gradle-plugin", versionName)

    pom {
        name.set("Udytils Metrics - Gradle plugin")
        description.set("Gradle plugin collecting codebase health metrics into a git-branch store with an HTML trend report.")
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
