package dev.isaacudy.udytils.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.isaacudy.udytils.UdytilsConfig
import dev.isaacudy.udytils.application

fun Permission.Location.hasPreciseLocation(): Boolean {
    return ContextCompat.checkSelfPermission(
        UdytilsConfig.application,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

fun Permission.Location.hasApproximateLocation(): Boolean {
    return ContextCompat.checkSelfPermission(
        UdytilsConfig.application,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

fun Permission.Location.hasBackgroundLocation(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return true
    }
    return ContextCompat.checkSelfPermission(
        UdytilsConfig.application,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}