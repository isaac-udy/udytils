package dev.isaacudy.udytils.permissions

/** Pure policy shared by the native permission observer and its regression tests. */
internal data class IosLocationPermissionState(
    val authorization: Authorization,
    val precise: Boolean,
) {
    enum class Authorization { NotDetermined, Denied, Restricted, WhenInUse, Always }
    enum class Request { None, WhenInUse, Always, Settings }

    fun grants(permission: Permission.Location): Boolean {
        val authorized = authorization == Authorization.Always ||
            (!permission.requireBackground && authorization == Authorization.WhenInUse)
        return authorized && (!permission.requirePrecise || precise)
    }

    fun request(permission: Permission.Location): Request = when {
        grants(permission) -> Request.None
        // Asking for Always first can report provisional Always before the real upgrade prompt.
        authorization == Authorization.NotDetermined -> Request.WhenInUse
        authorization == Authorization.WhenInUse && permission.requireBackground -> Request.Always
        else -> Request.Settings
    }
}
