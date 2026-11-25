package dev.isaacudy.udytils.mutation

/**
 * The switch that mutated code reads. Mutant ID 0 selects the original code.
 *
 * Each host reads the starting ID from the `UDYTILS_MUTANT` environment variable (iOS simulator:
 * `SIMCTL_CHILD_UDYTILS_MUTANT`; JVM: the `udytils.mutant` system property first; browser: the
 * Karma client config).
 */
object Mutants {
    var active: Long = readActiveMutant()

    /** While true, every mutant site that runs adds its ID to [hits]. */
    var recordHits: Boolean = false

    fun hits(): Set<Long> = hitSet.snapshot()

    fun clearHits() = hitSet.clear()

    internal fun check(id: Long): Boolean {
        if (recordHits) hitSet.add(id)
        return id == active
    }
}

/** Returns true while mutant [id] is active. The mutation compiler plugin inserts the calls. */
fun mutantActive(id: Long): Boolean = Mutants.check(id)

internal expect fun readActiveMutant(): Long

internal expect class HitSet() {
    fun add(id: Long)
    fun snapshot(): Set<Long>
    fun clear()
}

private val hitSet = HitSet()
