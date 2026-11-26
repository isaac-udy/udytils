package dev.isaacudy.udytils.trace

internal actual object CurrentSpan {
    private val local = ThreadLocal<Span?>()

    actual var value: Span?
        get() = local.get()
        set(value) = local.set(value)
}
