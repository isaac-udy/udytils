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
    }
}

dependencies {
    // The DSL exposes Konsist types (Requirement predicates, scope/constrain checks) and the
    // test harnesses expose JUnit 5 dynamic-test + kotlin.test types — consumers program against
    // both, so they are api dependencies by design.
    api(libs.konsist)
    api(libs.junit.jupiterApi)
    api(libs.kotlin.testJunit5)
    // Construct<Group> owner resolution + @Describe reading.
    implementation(libs.kotlin.reflect)

    // JUnit 5 API + kotlin-test come in via the `api` dependencies above; the test task additionally
    // needs an engine on the runtime classpath to actually discover and run them.
    testRuntimeOnly(libs.junit.jupiterEngine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

mavenPublishing {
    pom {
        name.set("Udytils Architecture - Core")
        description.set("Architecture-as-code framework: a Konsist-based rule catalog DSL with generated documentation and JUnit 5 test harnesses")
        inceptionYear.set("2026")
    }
}
