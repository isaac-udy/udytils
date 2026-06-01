package dev.isaacudy.udytils.numbers

import kotlin.math.abs
import kotlin.math.pow

/**
 * Converts a Double to a String with a fixed number of decimal places.
 * Uses rounding to the nearest value.
 */
fun Double.toDecimalString(decimals: Int): String {
    val factor = 10.0.pow(decimals).toLong()

    // Shift, round, and use absolute value to simplify logic
    val shifted = (abs(this) * factor + 0.5).toLong()

    val integralPart = shifted / factor
    val fractionalPart = shifted % factor

    // Ensure leading zeros in decimals (e.g., .05 instead of .5)
    val fractionalString = if (decimals > 0) {
        fractionalPart.toString().padStart(decimals, '0')
    } else ""

    val sign = if (this < 0) "-" else ""

    return if (decimals <= 0) {
        "$sign$integralPart"
    } else {
        "$sign$integralPart.$fractionalString"
    }
}

