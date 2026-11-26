package dev.isaacudy.udytils.mutation.gradle

/**
 * Per-operator sampling: a run includes a selected mutant when the mutant's ID, mixed with the seed,
 * falls below the operator's fraction. The same seed selects the same mutants in every run.
 */
internal object Sampling {
    fun keeps(id: Long, fraction: Double, seed: Long): Boolean {
        if (fraction >= 1.0) return true
        val unit = (mix(id xor mix(seed)) ushr 11).toDouble() / (1L shl 53).toDouble()
        return unit < fraction
    }

    /** Parses `remove-call=0.1,elvis=0.5`. */
    fun parse(value: String, source: String): Map<String, Double> =
        value.split(',').map(String::trim).filter(String::isNotEmpty).associate { entry ->
            val (operator, fraction) = entry.split('=').map(String::trim).takeIf { it.size == 2 }
                ?: throw IllegalArgumentException("$source entry \"$entry\" must be operator=fraction, for example remove-call=0.1.")
            operator to (fraction.toDoubleOrNull() ?: throw IllegalArgumentException("$source entry \"$entry\" has no number after =."))
        }

    fun requireFraction(operator: String, fraction: Double, source: String) {
        require(fraction > 0.0 && fraction <= 1.0) {
            "$source samples \"$operator\" at $fraction. A fraction must be greater than 0 and at most 1."
        }
    }

    private fun mix(value: Long): Long {
        var z = value + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
