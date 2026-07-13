package dev.isaacudy.udytils

/**
 * Returns this value if it is non-null, otherwise throws an [IllegalArgumentException].
 * Fluent alternative to `requireNotNull(value)` for the end of a call chain.
 */
fun <T : Any> T?.requireNotNull(): T {
    return requireNotNull(this)
}

/**
 * Returns this value if it is non-null, otherwise throws an [IllegalArgumentException] with the
 * message produced by [lazyMessage].
 */
fun <T : Any> T?.requireNotNull(lazyMessage: () -> String): T {
    return requireNotNull(this, lazyMessage)
}
