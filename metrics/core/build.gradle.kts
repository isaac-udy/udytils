import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
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
    // The model types and JSON codec are the contract integrations program against.
    api(libs.kotlinx.serialization)
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "dev.isaacudy.udytils",
        artifactId = "metrics-core",
        version = versionName,
    )

    pom {
        name.set("udytils metrics core")
        description.set("Model, store, and report rendering for codebase health metrics")
        url.set("https://github.com/isaac-udy/udytils")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("isaac-udy")
                name.set("Isaac Udy")
                url.set("https://github.com/isaac-udy/")
            }
        }
        scm {
            url.set("https://github.com/isaac-udy/udytils")
            connection.set("scm:git:git://github.com/isaac-udy/udytils.git")
            developerConnection.set("scm:git:ssh://git@github.com/isaac-udy/udytils.git")
        }
    }
}
