package dev.isaacudy.udytils.permissions

import dev.enro.NavigationKey
import kotlinx.serialization.Serializable

/**
 * Enro navigation key that requests [permission] from the user and completes with a
 * [PermissionStatus].
 *
 * ```
 * val requestPermission = registerForNavigationResult<PermissionStatus<Permission>> { status ->
 *     if (status is PermissionStatus.Granted) startCamera()
 * }
 * requestPermission.open(RequestPermissionDestination(Permission.Camera))
 * ```
 *
 * ## Platform support
 * - **Android**: implemented — shows the system request dialog (with a rationale/settings flow
 *   for permanently denied permissions) and delivers a [PermissionStatus] result.
 * - **iOS**: implemented for [Permission.Location], [Permission.Camera] and
 *   [Permission.Microphone] — triggers the system prompt, but does not deliver a result through
 *   Enro; observe the outcome with [rememberHasPermission] instead.
 * - **Desktop JVM / wasmJs**: NOT implemented — no destination is bound for this key on those
 *   platforms, and [hasPermission] itself currently throws [NotImplementedError] there.
 */
@Serializable
data class RequestPermissionDestination<T: Permission>(
    val permission: Permission,
) : NavigationKey.WithResult<PermissionStatus<T>>
