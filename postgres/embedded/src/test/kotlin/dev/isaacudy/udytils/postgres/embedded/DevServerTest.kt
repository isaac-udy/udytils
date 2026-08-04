package dev.isaacudy.udytils.postgres.embedded

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Seed-once semantics, end to end against real Postgres. `DevServer.launch` is used rather
 * than `DevServer.start` so each test owns its own server — the public entry point is a
 * JVM-wide singleton by design.
 */
class DevServerTest {

    private val baseDirectory = tempDirectory("dev-server")

    @AfterTest
    fun cleanUp() {
        baseDirectory.deleteRecursivelyQuietly()
    }

    /** Inserts one row, and counts how many times it was asked to. */
    private class RecordingScenario(override val name: String) : DevScenario {
        override val description: String = "Inserts one widget."
        val applications = AtomicInteger(0)

        override suspend fun apply(database: Database) {
            applications.incrementAndGet()
            suspendTransaction(db = database) {
                exec("INSERT INTO widgets (name) VALUES ('$name')")
            }
        }
    }

    private fun persistentConfig(
        freshScenario: DevScenario = EmptyScenario,
        requestedScenario: DevScenario? = null,
        port: Int,
    ) = DevServerConfig(
        storage = DevServerStorage.Persistent(baseDirectory = baseDirectory, port = port),
        freshScenario = freshScenario,
        requestedScenario = requestedScenario,
    )

    @Test
    fun freshClusterIsSeededAndReopeningItIsNot() {
        val port = freePort()
        val scenario = RecordingScenario("default")

        val first = DevServer.launch(persistentConfig(freshScenario = scenario, port = port))
        try {
            assertTrue(first.freshlyInitialized)
            assertEquals(scenario, first.appliedScenario)
            assertEquals(1, scenario.applications.get())
            assertEquals(1, first.postgresConfig.countRows("widgets"))
            assertEquals(port, first.port)
            // The cluster directory carries the binaries' major version so a bump starts fresh.
            assertTrue(
                first.dataDirectory!!.fileName.toString().startsWith("pg"),
                first.dataDirectory.toString(),
            )
            assertEquals(baseDirectory, first.dataDirectory.parent)
        } finally {
            first.close()
        }

        val second = DevServer.launch(persistentConfig(freshScenario = scenario, port = port))
        try {
            assertFalse(second.freshlyInitialized)
            assertNull(second.appliedScenario, "an existing database must not be re-seeded")
            assertEquals(1, scenario.applications.get())
            assertEquals(1, second.postgresConfig.countRows("widgets"), "the seeded row survived the restart")
        } finally {
            second.close()
        }
    }

    @Test
    fun explicitlyRequestedScenarioOverExistingDataFails() {
        val port = freePort()

        DevServer.launch(persistentConfig(port = port)).close()

        val requested = RecordingScenario("demo")
        val failure = assertFailsWith<IllegalStateException> {
            DevServer.launch(persistentConfig(requestedScenario = requested, port = port))
        }
        val message = failure.message.orEmpty()
        assertTrue("'demo'" in message, message)
        assertTrue("wipe" in message, message)
        assertTrue("already contains data" in message, message)
        assertEquals(0, requested.applications.get())

        // The failed launch must not leave a postmaster holding the port.
        DevServer.launch(persistentConfig(port = port)).close()
    }

    /**
     * The singleton entry point, which is what a hot reload leans on: the second call must
     * not restart Postgres or re-seed. Deliberately left open — the JVM shutdown hook
     * registered by [DevServer.start] closes it when the test JVM exits, which is exactly
     * the lifecycle an application gets.
     */
    @Test
    fun startIsIdempotentForTheLifeOfTheJvm() {
        val scenario = RecordingScenario("singleton")
        val config = DevServerConfig(
            storage = DevServerStorage.Ephemeral,
            requestedScenario = scenario,
        )

        val first = DevServer.start(config)
        val second = DevServer.start(config)

        assertSame(first, second, "a reload must get the same database back, not a new one")
        assertEquals(1, scenario.applications.get())
        assertEquals(1, first.postgresConfig.countRows("widgets"))
    }

    @Test
    fun ephemeralModeSeedsEveryStart() {
        val scenario = RecordingScenario("demo")
        val config = DevServerConfig(
            storage = DevServerStorage.Ephemeral,
            requestedScenario = scenario,
        )

        DevServer.launch(config).use { handle ->
            assertTrue(handle.freshlyInitialized)
            assertNull(handle.dataDirectory, "ephemeral runs have no directory to point a wipe task at")
            assertEquals(scenario, handle.appliedScenario)
            assertEquals(1, handle.postgresConfig.countRows("widgets"))
            assertFalse(handle.isClosed)
        }
    }
}
