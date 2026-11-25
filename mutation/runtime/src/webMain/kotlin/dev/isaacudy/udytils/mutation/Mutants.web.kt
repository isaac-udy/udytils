package dev.isaacudy.udytils.mutation

internal actual fun readActiveMutant(): Long = readMutantSetting()?.toLongOrNull() ?: 0L

internal actual class HitSet actual constructor() {
    private val ids = mutableSetOf<Long>()
    actual fun add(id: Long) { ids.add(id) }
    actual fun snapshot(): Set<Long> = ids.toSet()
    actual fun clear() = ids.clear()
}

internal expect fun readMutantSetting(): String?
