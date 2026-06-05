import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
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
    configure(GradlePlugin(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.isaacudy.udytils", "postgres-gradle-plugin", versionName)

    pom {
        name.set("Udytils Postgres - Gradle plugin")
        description.set("Gradle plugin wiring the udytils Postgres codegen into a build.")
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
