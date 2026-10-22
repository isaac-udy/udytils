package dev.isaacudy.udytils.urpc

/** The state of a client's streaming connection, as [UrpcClientFactory.connectionStatus] reports it. */
enum class UrpcConnectionStatus {
    /** No streaming call needs the connection, or its connection gate is closed. */
    Idle,

    /** Calls are waiting and no connection has opened since they started waiting. */
    Connecting,

    Connected,

    /** The connection dropped while calls still need it; they re-open once it is back. */
    Reconnecting,
}
