package dev.isaacudy.udytils.permissions

/**
 * The outcome of a permission request, delivered as the result of a
 * [RequestPermissionDestination].
 */
sealed interface PermissionStatus<out T : Permission> {
    val permission: T

    /** The user granted [permission]. */
    data class Granted<T : Permission>(
        override val permission: T
    ) : PermissionStatus<T>

    /** The user denied [permission]; it may be requested again. */
    data class Denied<T : Permission>(
        override val permission: T
    ) : PermissionStatus<T>

    /**
     * The user permanently denied [permission] — the system will no longer show a request
     * dialog, so granting requires the user to visit the app's settings.
     */
    data class DeniedPermanently<T : Permission>(
        override val permission: T
    ) : PermissionStatus<T>
}