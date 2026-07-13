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

// Bake the version into a resource so the plugin can resolve the matching
// codegen-engine / runtime artifact versions at apply time.
tasks.named<ProcessResources>("processResources") {
    val v = versionName
    inputs.property("version", v)
    filesMatching("**/version.properties") {
        expand("version" to v)
    }
}

// `java-gradle-plugin` puts gradleApi() on the classpath and generates the
// plugin marker that lets an included build contribute this plugin.
gradlePlugin {
    plugins {
        create("postgresCodegen") {
            id = "dev.isaacudy.udytils.postgres"
            implementationClass = "dev.isaacudy.udytils.postgres.gradle.PostgresCodegenPlugin"
            displayName = "Udytils Postgres codegen"
            description = "Flyway migrations -> embedded-Postgres schema snapshot -> generated Exposed Table/Row sources."
        }
    }
}

mavenPublishing {
    pom {
        name.set("Udytils Postgres - Gradle plugin")
        description.set("Gradle plugin wiring the udytils Postgres codegen into a build.")
        inceptionYear.set("2026")
    }
}
