import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
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

// The plugin adds the runtime, compiler plugin and test driver artifacts of the same version.
tasks.named<ProcessResources>("processResources") {
    val v = versionName
    inputs.property("version", v)
    filesMatching("**/version.properties") {
        expand("version" to v)
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:${libs.versions.kotlin.get()}")
    implementation(libs.kotlinx.serialization)

    testImplementation(libs.kotlin.testJunit5)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("mutation") {
            id = "dev.isaacudy.udytils.mutation"
            implementationClass = "dev.isaacudy.udytils.mutation.gradle.MutationPlugin"
            displayName = "Udytils mutation testing"
            description = "Compiles mutants into Kotlin code once and runs each mutant against the tests that execute it."
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils Mutation - Gradle plugin")
        description.set("Gradle plugin that compiles catalog mutants into Kotlin code and runs them against the tests that execute them.")
        inceptionYear.set("2026")
    }
}
