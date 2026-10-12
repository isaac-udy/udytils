package dev.isaacudy.udytils.error

import kotlin.reflect.KClass

// Kotlin/JS has no KClass.qualifiedName.
internal actual val KClass<*>.qualifiedNameOrNull: String? get() = null
