package dev.isaacudy.udytils

fun <T : Any> T?.requireNotNull(): T {
    return requireNotNull(this)
}

fun <T : Any> T?.requireNotNull(lazyMessage: () -> String): T {
    return requireNotNull(this, lazyMessage)
}
