package dev.isaacudy.udytils.trace

import kotlinx.coroutines.Job

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
internal actual object CurrentSpan {
    actual var value: Span? = null
    actual var handoff: Span? = null
    actual var handoffJob: Job? = null
}
