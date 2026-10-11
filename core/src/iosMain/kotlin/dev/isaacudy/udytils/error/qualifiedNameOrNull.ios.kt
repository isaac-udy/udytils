package dev.isaacudy.udytils.error

import kotlin.reflect.KClass

internal actual val KClass<*>.qualifiedNameOrNull: String? get() = qualifiedName
