import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    id("udytils.publish")
    `java-gradle-plugin`
}

val versionName = libs.versions.udytilsVersionName.get()

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// Bake the version into a resource so the plugin can add the matching
// atlas-core artifact at apply time.
tasks.named<ProcessResources>("processResources") {
    val v = versionName
    inputs.property("version", v)
    filesMatching("**/version.properties") {
        expand("version" to v)
    }
}

dependencies {
    // The task calls atlas-core's generateAtlas() directly. In a composite build
    // this resolves to the local :atlas-core project; when published, the
    // udytils.publish convention rewrites the coordinate to the Maven artifact.
    implementation(project(":atlas-core"))
}

gradlePlugin {
    plugins {
        create("atlas") {
            id = "dev.isaacudy.udytils.atlas"
            implementationClass = "dev.isaacudy.udytils.atlas.gradle.AtlasPlugin"
            displayName = "Udytils UI Atlas"
            description = "Generates an interactive UI atlas from Enro navigation destinations and Paparazzi snapshot goldens."
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils Atlas - Gradle plugin")
        description.set("Gradle plugin wiring the udytils UI atlas generator into a build.")
        inceptionYear.set("2026")
    }
}
