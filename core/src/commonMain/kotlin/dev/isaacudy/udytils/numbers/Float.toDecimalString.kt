package dev.isaacudy.udytils.numbers

/**
 * Converts a Float to a String with a fixed number of decimal places.
 */
fun Float.toDecimalString(decimals: Int): String {
    return this.toDouble().toDecimalString(decimals)
}