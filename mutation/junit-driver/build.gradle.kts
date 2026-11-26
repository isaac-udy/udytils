plugins {
    `java-library`
    id("udytils.publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // The test classpath that the driver runs on provides the launcher.
    compileOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

mavenPublishing {
    pom {
        name.set("Udytils Mutation - JUnit driver")
        description.set("Runs JUnit Platform tests in one JVM while the mutation runner switches the active mutant.")
        inceptionYear.set("2026")
    }
}
