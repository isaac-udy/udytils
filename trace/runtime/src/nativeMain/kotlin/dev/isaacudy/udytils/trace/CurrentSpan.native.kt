package dev.isaacudy.udytils.trace

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
internal actual object CurrentSpan {
    actual var value: Span? = null
}
