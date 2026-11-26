package dev.isaacudy.udytils.trace

import kotlinx.coroutines.Job

internal actual object CurrentSpan {
    private class Slots {
        var value: Span? = null
        var handoff: Span? = null
        var handoffJob: Job? = null
    }

    private val local = ThreadLocal.withInitial(::Slots)

    actual var value: Span?
        get() = local.get().value
        set(value) { local.get().value = value }

    actual var handoff: Span?
        get() = local.get().handoff
        set(value) { local.get().handoff = value }

    actual var handoffJob: Job?
        get() = local.get().handoffJob
        set(value) { local.get().handoffJob = value }
}
