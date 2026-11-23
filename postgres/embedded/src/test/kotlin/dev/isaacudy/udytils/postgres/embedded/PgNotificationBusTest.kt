package dev.isaacudy.udytils.postgres.embedded

import dev.isaacudy.udytils.postgres.PgNotificationBus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// A poll timeout far above every bound below: a test passes only if the bus does not wait out a poll.
private const val POLL_TIMEOUT_MS = 30_000

class PgNotificationBusTest {

    private val postgres = EmbeddedPostgresLifecycle()
    private val template = MigratedTemplate(postgres)
    private val database = template.clone()
    private val config = database.config

    @AfterTest
    fun cleanUp() {
        database.close()
        template.close()
        postgres.close()
    }

    private fun bus() = PgNotificationBus(config.jdbcUrl, config.username, config.password, pollTimeoutMs = POLL_TIMEOUT_MS)

    @Test
    fun awaitListeningOnANewChannelDoesNotWaitForThePoll() = runBlocking {
        val bus = bus()
        try {
            withTimeout(10.seconds) { bus.awaitListening("first") }

            withTimeout(5.seconds) { bus.awaitListening("second") }
            val received = async(start = CoroutineStart.UNDISPATCHED) { bus.listen("second").first() }
            config.execute("NOTIFY second, 'payload'")

            assertEquals("payload", withTimeout(5.seconds) { received.await() })
        } finally {
            bus.close()
        }
    }

    @Test
    fun closeEndsTheListenerSession() = runBlocking {
        val bus = bus()
        withTimeout(10.seconds) { bus.awaitListening("changes") }
        assertEquals(1, listenerSessions())

        bus.close()

        val deadline = TimeSource.Monotonic.markNow() + 5.seconds
        while (listenerSessions() > 0 && !deadline.hasPassedNow()) Thread.sleep(20)
        assertEquals(0, listenerSessions())
    }

    private fun listenerSessions(): Int =
        config.query("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()") { rows ->
            rows.next()
            rows.getInt(1)
        }
}
