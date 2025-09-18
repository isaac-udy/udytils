package dev.isaacudy.udytils.permissions

import android.Manifest
import android.os.Build

val Permission.androidPermission : String get() {
    return when (this) {
        Permission.Location.Approximate -> Manifest.permission.ACCESS_COARSE_LOCATION
        Permission.Location.Precise -> Manifest.permission.ACCESS_FINE_LOCATION
        Permission.NearbyDevices -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.NEARBY_WIFI_DEVICES
            else -> "android.permission.NEARBY_WIFI_DEVICES"
        }
        Permission.Phone -> Manifest.permission.READ_PHONE_STATE
        Permission.Camera -> Manifest.permission.CAMERA
    }
}