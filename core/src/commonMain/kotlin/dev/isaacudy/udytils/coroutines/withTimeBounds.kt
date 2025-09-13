package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * Executes a suspending function, ensuring its total duration is constrained by minimum and maximum values.
 *
 * @param minimumBound The minimum duration in milliseconds. If the [block] completes faster,
 * the function will delay until this minimum has been reached.
 * @param maximumBound The maximum duration in milliseconds. If the [block] takes longer than
 * this, the function will return immediately without any further delay.
 * @param block The suspending function to be executed.
 */
suspend fun <T> withTimeBounds(
    minimumBound: Long = 125,
    maximumBound: Long = 1000,
    block: suspend () -> T,
) : T {
    require(minimumBound <= maximumBound) { "minimumBound must be less than or equal to maximumBound" }
    val startTime = Clock.System.now()
    val result = block()
    val executionTime = Clock.System.now() - startTime

    if (executionTime.inWholeMilliseconds > maximumBound) return result
    if (executionTime.inWholeMilliseconds < minimumBound) return result
    val remainingDelay = maximumBound - executionTime.inWholeMilliseconds
    delay(remainingDelay)
    return result
}