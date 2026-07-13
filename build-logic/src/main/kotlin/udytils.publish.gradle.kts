import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Publishing convention for every published udytils module.
 *
 * Owns everything that must be identical across modules: group, version, Maven Central +
 * signing setup, artifact coordinates (derived from the project path, `:urpc:client` →
 * `urpc-client`), and the shared POM metadata (license, developer, SCM).
 *
 * The applying module supplies only what is unique to it:
 * ```
 * mavenPublishing {
 *     pom {
 *         name.set("Udytils Core")
 *         description.set("Core utilities for Kotlin Multiplatform development")
 *         inceptionYear.set("2025")
 *     }
 * }
 * ```
 *
 * The publication platform (KMP / Kotlin JVM / Gradle plugin) is auto-detected by the
 * vanniktech plugin from the module's other plugins; none of the modules use Dokka, so
 * detection yields an empty javadoc jar plus a sources jar for every module type.
 */

plugins {
    id("com.vanniktech.maven.publish")
}

private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "dev.isaacudy.udytils"
version = libs.findVersion("udytilsVersionName").get().requiredVersion

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "dev.isaacudy.udytils",
        artifactId = project.path.removePrefix(":").replace(":", "-"),
        version = version.toString(),
    )

    pom {
        url.set("https://github.com/isaac-udy/udytils")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("isaac-udy")
                name.set("Isaac Udy")
                url.set("https://github.com/isaac-udy")
            }
        }
        scm {
            url.set("https://github.com/isaac-udy/udytils")
            connection.set("scm:git:git://github.com/isaac-udy/udytils.git")
            developerConnection.set("scm:git:ssh://git@github.com/isaac-udy/udytils.git")
        }
    }
}
