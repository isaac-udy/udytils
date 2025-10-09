package dev.isaacudy.udytils.permissions

import dev.enro.NavigationKey
import kotlinx.serialization.Serializable

@Serializable
data class RequestPermissionDestination<T: Permission>(
    val permission: Permission,
) : NavigationKey.WithResult<PermissionStatus<T>>
