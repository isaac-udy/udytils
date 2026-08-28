package dev.isaacudy.udytils.architecture.gradle

import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ArchitecturePluginTest {

    @TempDir
    lateinit var projectDir: File

    private fun project() = ProjectBuilder.builder()
        .withProjectDir(projectDir)
        .build()
        .also {
            it.plugins.apply("java")
            it.plugins.apply(ArchitecturePlugin::class.java)
        }

    @Test
    fun `auditArchitecture task is registered`() {
        val project = project()
        val task = project.tasks.findByName("auditArchitecture")
        assertNotNull(task, "auditArchitecture task should be registered")
        assertTrue(task is TestTask, "auditArchitecture should be a Test task")
        assertEquals("verification", (task as TestTask).group)
    }

    @Test
    fun `verifyArchitecture task is registered`() {
        val project = project()
        val task = project.tasks.findByName("verifyArchitecture")
        assertNotNull(task, "verifyArchitecture task should be registered")
        assertTrue(task is TestTask, "verifyArchitecture should be a Test task")
        assertEquals("verification", (task as TestTask).group)
    }

    @Test
    fun `auditArchitecture uses JUnit tag filter`() {
        val project = project()
        val task = project.tasks.getByName("auditArchitecture") as TestTask
        val options = task.options as org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
        assertTrue(options.includeTags.contains("audit"))
    }

    @Test
    fun `auditArchitecture shows standard streams`() {
        val project = project()
        val task = project.tasks.getByName("auditArchitecture") as TestTask
        assertTrue(task.testLogging.showStandardStreams)
    }
}
