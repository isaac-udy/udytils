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
// architecture-core artifact at apply time.
tasks.named<ProcessResources>("processResources") {
    val v = versionName
    inputs.property("version", v)
    filesMatching("**/version.properties") {
        expand("version" to v)
    }
}

gradlePlugin {
    plugins {
        create("architecture") {
            id = "dev.isaacudy.udytils.architecture"
            implementationClass = "dev.isaacudy.udytils.architecture.gradle.ArchitecturePlugin"
            displayName = "Udytils Architecture"
            description = "Wires the udytils architecture framework: an architectureTest source set plus standalone verifyArchitecture / updateArchitectureDocumentation tasks."
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils Architecture - Gradle plugin")
        description.set("Gradle plugin wiring the udytils architecture framework into a build.")
        inceptionYear.set("2026")
    }
}
