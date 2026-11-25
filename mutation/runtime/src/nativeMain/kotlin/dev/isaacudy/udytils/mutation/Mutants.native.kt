@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package dev.isaacudy.udytils.mutation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal actual fun readActiveMutant(): Long = getenv("UDYTILS_MUTANT")?.toKString()?.toLongOrNull() ?: 0L

internal actual class HitSet actual constructor() {
    private val ids = AtomicReference(emptySet<Long>())
    actual fun add(id: Long) {
        while (true) {
            val current = ids.load()
            if (id in current || ids.compareAndSet(current, current + id)) return
        }
    }
    actual fun snapshot(): Set<Long> = ids.load()
    actual fun clear() = ids.store(emptySet())
}
