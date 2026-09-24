package dev.isaacudy.udytils.htmx

import kotlin.time.Duration

/**
 * One `hx-trigger` specification. Event filters (`click[ctrlKey]`) are JavaScript expressions and
 * are deliberately not expressible.
 */
class Trigger private constructor(val value: String) {

    fun changed(): Trigger = Trigger("$value changed")

    fun once(): Trigger = Trigger("$value once")

    fun delay(duration: Duration): Trigger = Trigger("$value delay:${duration.htmx}")

    fun throttle(duration: Duration): Trigger = Trigger("$value throttle:${duration.htmx}")

    fun from(target: HxTarget): Trigger = Trigger("$value from:${target.value}")

    fun consume(): Trigger = Trigger("$value consume")

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is Trigger && other.value == value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        val Load: Trigger = Trigger("load")
        val Revealed: Trigger = Trigger("revealed")
        val Intersect: Trigger = Trigger("intersect")

        fun on(event: String): Trigger {
            require(eventName.matches(event)) { "`$event` is not an event name" }
            return Trigger(event)
        }

        fun every(interval: Duration): Trigger = Trigger("every ${interval.htmx}")

        /** A named server-sent event, for elements inside an [Sse.connect] root. */
        fun sse(event: String): Trigger = Trigger("sse:${on(event).value}")

        private val eventName = Regex("[A-Za-z][A-Za-z0-9_:.-]*")
    }
}
