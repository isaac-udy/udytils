package dev.isaacudy.udytils.error

import kotlin.reflect.KClass

// KClass.qualifiedName is a compile error on Kotlin/JS, which has no qualified names.
internal expect val KClass<*>.qualifiedNameOrNull: String?
