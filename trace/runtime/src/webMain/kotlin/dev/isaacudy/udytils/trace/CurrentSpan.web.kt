package dev.isaacudy.udytils.trace

internal actual object CurrentSpan {
    actual var value: Span? = null
}
