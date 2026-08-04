package dev.isaacudy.udytils.postgres.embedded

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Boots real Postgres binaries, so each test costs an `initdb` + start. Kept to the two
 * behaviours the persistent mode adds: data survives a restart, and a restart is
 * recognisable as a restart.
 */
class EmbeddedPostgresLifecycleTest {

    private val dataDirectory = tempDirectory("embedded-pg-lifecycle")

    @AfterTest
    fun cleanUp() {
        dataDirectory.deleteRecursivelyQuietly()
    }

    @Test
    fun persistentDataDirectorySurvivesRestartOnAFixedPort() {
        val port = freePort()

        EmbeddedPostgresLifecycle(dataDirectory = dataDirectory, port = port).use { first ->
            assertTrue(first.freshlyInitialized, "a brand-new data directory is freshly initialised")
            assertEquals(port, first.port, "the fixed port must be honoured")
            first.config.execute("CREATE TABLE survivors (name text NOT NULL)")
            first.config.execute("INSERT INTO survivors (name) VALUES ('first run')")
        }

        assertTrue(
            Files.exists(dataDirectory.resolve("PG_VERSION")),
            "closing a persistent lifecycle must not delete the data directory",
        )

        EmbeddedPostgresLifecycle(dataDirectory = dataDirectory, port = port).use { second ->
            assertFalse(second.freshlyInitialized, "reopening an existing cluster is not a fresh init")
            assertEquals(port, second.port)
            assertEquals(1, second.config.countRows("survivors"))
            assertEquals(
                "first run",
                second.config.query("SELECT name FROM survivors") { rows ->
                    rows.next()
                    rows.getString(1)
                },
            )
        }
    }

    @Test
    fun ephemeralLifecycleIsAlwaysFreshlyInitialized() {
        EmbeddedPostgresLifecycle().use { lifecycle ->
            assertTrue(lifecycle.freshlyInitialized)
            assertTrue(lifecycle.port > 0)
            assertTrue(lifecycle.serverVersion.isNotBlank())
            assertTrue(lifecycle.config.jdbcUrl.endsWith("/postgres"))
        }
    }

    @Test
    fun bundledBinariesReportAMajorVersion() {
        val major = ZonkyBinaries.majorVersionOrNull(javaClass.classLoader)
        assertTrue(
            major != null && major >= 10,
            "expected a plausible Postgres major version from the binaries on the classpath, got $major",
        )
    }
}
