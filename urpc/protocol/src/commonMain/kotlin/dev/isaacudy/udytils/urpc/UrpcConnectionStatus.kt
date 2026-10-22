package dev.isaacudy.udytils.urpc

/** The state of a client's streaming connection, as [UrpcClientFactory.connectionStatus] reports it. */
sealed interface UrpcConnectionStatus {
    /** No streaming call needs the connection. */
    data object Idle : UrpcConnectionStatus

    /** Calls are waiting, but the connection gate is closed (e.g. the app is in the background). */
    data object Paused : UrpcConnectionStatus

    /**
     * Calls are waiting and no connection has opened since they started waiting.
     * [attempt] counts the failed attempts so far; [lastFailure] is the most recent one's cause.
     */
    data class Connecting(val attempt: Int = 0, val lastFailure: Throwable? = null) : UrpcConnectionStatus

    data object Connected : UrpcConnectionStatus

    /**
     * The connection dropped while calls still need it; they re-open once it is back. [attempt]
     * counts failed attempts since the drop; [lastFailure] is null after a clean close.
     */
    data class Reconnecting(val attempt: Int = 0, val lastFailure: Throwable? = null) : UrpcConnectionStatus

    /**
     * The server, or something in front of it, refused the connection in a way retrying won't fix.
     * The client stops trying until [UrpcClientFactory.reconnectNow].
     */
    data class Failed(val cause: UrpcConnectionRejectedException) : UrpcConnectionStatus
}

/**
 * The streaming connection was refused for good: by an HTTP 4xx on the connection's address
 * ([statusCode]), or by the server closing it with a code in [URPC_REJECTED_CLOSE_CODES] ([closeCode]).
 */
class UrpcConnectionRejectedException(
    val statusCode: Int? = null,
    val closeCode: Short? = null,
) : RuntimeException(
    when {
        statusCode != null -> "urpc connection refused with HTTP $statusCode"
        else -> "urpc connection closed by the server with code $closeCode"
    },
)

/** Close codes a server uses to refuse a client for good; the client stops reconnecting. */
val URPC_REJECTED_CLOSE_CODES: IntRange = 4400..4499

/**
 * The call name of the client's heartbeat. A server answers its `Open` with `Complete`; a server
 * that predates the heartbeat answers with `Error`, which proves the connection alive just as well.
 */
const val URPC_PING_WIRE_NAME: String = "urpc.ping"

/** What a server answers to a plain GET on its streaming address, telling a probe the route exists. */
const val URPC_PROBE_STATUS: Int = 426
