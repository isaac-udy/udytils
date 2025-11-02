package dev.isaacudy.udytils.permissions

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.isaacudy.udytils.android.Udytils

actual fun hasPermission(permission: Permission): Boolean {
    // If the Android Permission name is null, we can assume that we are running on a version
    // of Android where that particular permission doesn't exist, so we can assume that
    // any actions associated with the permission will work
    return permission.androidManifestPermissions.all { androidPermissionName ->
        ContextCompat.checkSelfPermission(
            Udytils.application,
            androidPermissionName,
        ) == PackageManager.PERMISSION_GRANTED
    }
}