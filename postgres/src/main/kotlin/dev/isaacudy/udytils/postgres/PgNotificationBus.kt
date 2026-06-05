package dev.isaacudy.udytils.postgres

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.postgresql.PGConnection
import org.postgresql.jdbc.PgConnection
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge from Postgres `LISTEN/NOTIFY` to Kotlin [SharedFlow]s.
 *
 * Holds a dedicated JDBC connection outside any pool (because a listener
 * connection must stay open forever and pool-returning it is meaningless). A
 * single background coroutine polls [PGConnection.getNotifications] on a
 * blocking thread and fans each notification out to the [SharedFlow]
 * registered for that channel.
 *
 * Usage:
 * ```
 * val flow: SharedFlow<String> = bus.listen("some_channel")
 * flow.filter { it == someKey }.collect { /* ... */ }
 * ```
 *
 * The listener reconnects automatically on connection failure. Channels added
 * at runtime are picked up on the next poll cycle.
 *
 * Channel names and payload semantics are entirely the caller's concern — this
 * class has no knowledge of them. The matching `pg_notify(channel, payload)`
 * triggers live in the consumer's migrations (see the `notifyTrigger` helper).
 *
 * Call [close] when the owning lifecycle (Ktor `Application`, test scope, etc.)
 * shuts down so the listener doesn't outlive its connection. The library does
 * not wire this for you — closing is the consumer's responsibility.
 */
class PgNotificationBus(
    private val jdbcUrl: String,
    private val username: String,
    private val password: String,
    private val extraBufferCapacity: Int = 128,
    private val reconnectDelayMs: Long = 2_000,
    private val pollTimeoutMs: Int = 500,
) : AutoCloseable {

    /** Convenience constructor for callers that already hold a [PostgresConfig]. */
    constructor(config: PostgresConfig) : this(config.jdbcUrl, config.username, config.password)

    private val logger = LoggerFactory.getLogger(PgNotificationBus::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flows = ConcurrentHashMap<String, MutableSharedFlow<String>>()

    init {
        scope.launch {
            while (isActive) {
                try {
                    runConnection()
                } catch (cancel: CancellationException) {
                    // Don't swallow cancellation — let the loop exit so a
                    // stopped owner doesn't leave a phantom listener thrashing
                    // on a dead port.
                    throw cancel
                } catch (t: Throwable) {
                    logger.warn("Postgres notification listener failed, reconnecting in {}ms: {}", reconnectDelayMs, t.message)
                    delay(reconnectDelayMs)
                }
            }
        }
    }

    /**
     * Subscribe to notifications on [channel]. The returned [SharedFlow] is
     * process-wide; multiple callers subscribing to the same channel share one
     * flow. Payloads are the raw strings sent by `pg_notify(channel, payload)`.
     */
    fun listen(channel: String): SharedFlow<String> =
        flows.computeIfAbsent(channel) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = extraBufferCapacity)
        }

    /**
     * Cancels the listener coroutine and closes its JDBC connection. Safe to
     * call multiple times.
     */
    override fun close() {
        scope.cancel()
    }

    private suspend fun runConnection() {
        DriverManager.getConnection(jdbcUrl, username, password).use { raw ->
            val conn = raw.unwrap(PgConnection::class.java)
            val subscribed = mutableSetOf<String>()

            // Subscribe to every channel that has a flow registered, plus any
            // added later (checked at the top of every poll iteration).
            while (scope.isActive) {
                val pending = flows.keys - subscribed
                for (ch in pending) {
                    conn.createStatement().use { it.execute("LISTEN \"$ch\"") }
                    subscribed += ch
                    logger.debug("Listening to channel '{}'", ch)
                }

                val notifications = conn.getNotifications(pollTimeoutMs)
                notifications?.forEach { n ->
                    flows[n.name]?.emit(n.parameter.orEmpty())
                }
            }
        }
    }
}
