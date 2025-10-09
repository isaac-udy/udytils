package dev.isaacudy.udytils.permissions

sealed interface PermissionStatus<out T : Permission> {
    val permission: T

    data class Granted<T : Permission>(
        override val permission: T
    ) : PermissionStatus<T>

    data class Denied<T : Permission>(
        override val permission: T
    ) : PermissionStatus<T>

    data class DeniedPermanently<T : Permission>(
        override val permission: T
    ) : PermissionStatus<T>
}