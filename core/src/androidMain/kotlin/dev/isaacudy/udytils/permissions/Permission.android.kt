package dev.isaacudy.udytils.permissions

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.isaacudy.udytils.UdytilsConfig
import dev.isaacudy.udytils.application

actual fun hasPermission(permission: Permission): Boolean {
    return ContextCompat.checkSelfPermission(
        UdytilsConfig.application,
        permission.androidPermission,
    ) == PackageManager.PERMISSION_GRANTED
}