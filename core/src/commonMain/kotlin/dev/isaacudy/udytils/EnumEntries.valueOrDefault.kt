package dev.isaacudy.udytils

import kotlin.enums.EnumEntries

/**
 * The enum entry whose name matches [name] exactly, or [default] when [name] is null or matches
 * nothing. A non-throwing alternative to `valueOf`, useful when parsing persisted or remote
 * enum names.
 */
fun <T : Enum<T>> EnumEntries<T>.valueOrDefault(name: String?, default: T): T {
    if (name == null) return default
    val found = find { it.name == name}
    if (found == null) return default
    return found
}