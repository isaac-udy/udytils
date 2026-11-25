package dev.isaacudy.udytils.mutation

import java.util.concurrent.ConcurrentHashMap

internal actual fun readActiveMutant(): Long =
    (System.getProperty("udytils.mutant") ?: System.getenv("UDYTILS_MUTANT"))?.toLongOrNull() ?: 0L

internal actual class HitSet actual constructor() {
    private val ids = ConcurrentHashMap.newKeySet<Long>()
    actual fun add(id: Long) { ids.add(id) }
    actual fun snapshot(): Set<Long> = ids.toSet()
    actual fun clear() = ids.clear()
}
