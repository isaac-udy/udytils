package dev.isaacudy.udytils.postgres

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The union-and-verify half of the fat-jar self-check is tested with fabricated service
 * files (a broken classloader is not worth building), plus one test proving a real,
 * healthy classpath — the one this test runs on — passes.
 */
class FlywayPluginManifestTest {

    private val coreServiceFile = """
        # flyway-core
        org.flywaydb.core.internal.publishing.PublishingConfigurationExtension
        org.flywaydb.core.internal.configuration.models.DryRunConfigurationExtension

        org.flywaydb.core.internal.resource.CoreResourceTypeProvider
    """.trimIndent()

    private val postgresServiceFile = """
        org.flywaydb.database.postgresql.PostgreSQLDatabaseType
        org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension
    """.trimIndent()

    @Test
    fun unionStripsCommentsBlanksAndDuplicates() {
        val entries = FlywayPluginManifest.union(
            listOf(coreServiceFile, postgresServiceFile, postgresServiceFile),
        )
        assertEquals(5, entries.size, entries.toString())
        assertTrue(entries.none { it.startsWith("#") }, entries.toString())
        assertTrue(entries.none { it.isBlank() }, entries.toString())
    }

    @Test
    fun bothArtifactsPresentPasses() {
        FlywayPluginManifest.verify(
            FlywayPluginManifest.union(listOf(coreServiceFile, postgresServiceFile)),
        )
    }

    @Test
    fun postgresEntriesClobberingCoreFails() {
        val failure = assertFailsWith<IllegalStateException> {
            FlywayPluginManifest.verify(FlywayPluginManifest.union(listOf(postgresServiceFile)))
        }
        val message = failure.message.orEmpty()
        assertTrue("org.flywaydb.core." in message, message)
        assertTrue("mergeServiceFiles" in message, message)
        assertTrue("unzip -p" in message, message)
        assertTrue("zero migrations" in message, message)
    }

    @Test
    fun coreEntriesClobberingPostgresFails() {
        val failure = assertFailsWith<IllegalStateException> {
            FlywayPluginManifest.verify(FlywayPluginManifest.union(listOf(coreServiceFile)))
        }
        assertTrue("org.flywaydb.database.postgresql" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun emptyManifestFails() {
        assertFailsWith<IllegalStateException> { FlywayPluginManifest.verify(emptySet()) }
    }

    /**
     * The healthy path: on a normal (non-fat) classpath both jars' service files are
     * visible, so the check a `PostgresMigrator` runs at boot must pass here.
     */
    @Test
    fun realClasspathIsHealthy() {
        val entries = FlywayPluginManifest.readFromClasspath(javaClass.classLoader)
        assertTrue(entries.isNotEmpty(), "no Flyway plugin manifest found on the test classpath")
        FlywayPluginManifest.verify(entries)
    }
}
