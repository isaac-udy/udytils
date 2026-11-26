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

// The plugin adds the runtime and compiler plugin artifacts of the same version.
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
        create("trace") {
            id = "dev.isaacudy.udytils.trace"
            implementationClass = "dev.isaacudy.udytils.trace.gradle.TracePlugin"
            displayName = "Udytils tracing"
            description = "Compiles span probes into a Kotlin module's main compilations."
        }
        create("traceSiteTable") {
            id = "dev.isaacudy.udytils.trace-site-table"
            implementationClass = "dev.isaacudy.udytils.trace.gradle.TraceSiteTablePlugin"
            displayName = "Udytils trace site table"
            description = "Generates the table that maps each trace site to its architecture Constructs."
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils Trace - Gradle plugin")
        description.set("Gradle plugins that compile span probes into Kotlin modules and generate the site to Construct table.")
        inceptionYear.set("2026")
    }
}
