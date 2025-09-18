package dev.isaacudy.udytils

import kotlin.enums.EnumEntries

fun <T : Enum<T>> EnumEntries<T>.valueOrDefault(name: String?, default: T): T {
    if (name == null) return default
    val found = find { it.name == name}
    if (found == null) return default
    return found
}