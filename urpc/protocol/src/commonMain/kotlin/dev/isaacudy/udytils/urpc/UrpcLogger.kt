package dev.isaacudy.udytils.urpc

/**
 * Lightweight logging hook used by the urpc client and server. Defaults to a no-op
 * implementation so the libraries do not pull in any logging framework. Consumers
 * can pass an adapter that delegates to slf4j, the Android logger, etc.
 */
interface UrpcLogger {
    fun debug(message: String) {}
    fun warn(message: String, throwable: Throwable? = null) {}
    fun error(message: String, throwable: Throwable? = null) {}

    object NoOp : UrpcLogger
}
