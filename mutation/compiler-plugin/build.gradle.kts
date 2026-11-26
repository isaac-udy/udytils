import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    id("udytils.publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")

    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    testImplementation(project(":mutation-runtime"))
    testImplementation(libs.kotlin.testJunit5)
}

tasks.test {
    useJUnitPlatform()
    // The test compiles Kotlin sources with the plugin, loaded from this module's own classes and resources.
    val pluginDirs = sourceSets.main.get().output
    dependsOn(pluginDirs)
    systemProperty("mutation.plugin.classpath", pluginDirs.files.joinToString(File.pathSeparator))
}

mavenPublishing {
    pom {
        name.set("Udytils Mutation - Kotlin compiler plugin")
        description.set("Kotlin IR plugin that compiles catalog mutants into the code behind a runtime switch.")
        inceptionYear.set("2026")
    }
}
